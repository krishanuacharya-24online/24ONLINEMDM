package com.e24online.mdm.service;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.records.SystemRuleCloneResult;
import com.e24online.mdm.repository.*;
import com.e24online.mdm.service.messaging.PolicyAuditPublisher;
import com.e24online.mdm.web.dto.PolicyAuditMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PoliciesCrudService {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private static final String ROLE_PRODUCT_ADMIN = "PRODUCT_ADMIN";
    private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    private static final String ROLE_TENANT_USER = "TENANT_USER";
    private static final Set<String> DECISION_ACTIONS = Set.of("ALLOW", "QUARANTINE", "BLOCK", "NOTIFY");
    private static final Set<String> MAPPING_SOURCES = Set.of("SYSTEM_RULE", "REJECT_APPLICATION", "TRUST_POLICY", "DECISION");
    private static final DateTimeFormatter RULE_CODE_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SystemInformationRuleRepository systemRuleRepository;
    private final SystemInformationRuleConditionRepository conditionRepository;
    private final RejectApplicationRepository rejectApplicationRepository;
    private final TrustScorePolicyRepository trustScorePolicyRepository;
    private final TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    private final RemediationRuleRepository remediationRuleRepository;
    private final RuleRemediationMappingRepository ruleRemediationMappingRepository;
    private final PolicyChangeAuditRepository policyChangeAuditRepository;
    private final PolicyAuditPublisher policyAuditPublisher;
    private final AuditEventService auditEventService;
    private final BlockingDb blockingDb;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public PoliciesCrudService(SystemInformationRuleRepository systemRuleRepository,
                               SystemInformationRuleConditionRepository conditionRepository,
                               RejectApplicationRepository rejectApplicationRepository,
                               TrustScorePolicyRepository trustScorePolicyRepository,
                               TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository,
                               RemediationRuleRepository remediationRuleRepository,
                               RuleRemediationMappingRepository ruleRemediationMappingRepository,
                               PolicyChangeAuditRepository policyChangeAuditRepository,
                               PolicyAuditPublisher policyAuditPublisher,
                               AuditEventService auditEventService,
                               BlockingDb blockingDb,
                               TransactionTemplate transactionTemplate,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.systemRuleRepository = systemRuleRepository;
        this.conditionRepository = conditionRepository;
        this.rejectApplicationRepository = rejectApplicationRepository;
        this.trustScorePolicyRepository = trustScorePolicyRepository;
        this.trustScoreDecisionPolicyRepository = trustScoreDecisionPolicyRepository;
        this.remediationRuleRepository = remediationRuleRepository;
        this.ruleRemediationMappingRepository = ruleRemediationMappingRepository;
        this.policyChangeAuditRepository = policyChangeAuditRepository;
        this.policyAuditPublisher = policyAuditPublisher;
        this.auditEventService = auditEventService;
        this.blockingDb = blockingDb;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public Flux<SystemInformationRule> listSystemRules(String status, int page, int size) {
        return listSystemRules(ROLE_PRODUCT_ADMIN, null, status, page, size);
    }

    public Flux<SystemInformationRule> listSystemRules(String role, String scopeTenantId, String status, int page, int size) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> systemRuleRepository.findPaged(tenantId, status, limit, offset));
    }

    public Mono<SystemInformationRule> getSystemRule(Long id) {
        return getSystemRule(ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<SystemInformationRule> getSystemRule(String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.mono(() -> {
            SystemInformationRule rule = systemRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System rule not found"));
            enforceReadable(normalizedRole, tenantId, rule.getTenantId(), "System rule not found");
            return rule;
        });
    }

    public Flux<SystemInformationRuleCondition> listSystemRuleConditions(Long id) {
        return listSystemRuleConditions(ROLE_PRODUCT_ADMIN, null, id);
    }

    public Flux<SystemInformationRuleCondition> listSystemRuleConditions(String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.flux(() -> {
            SystemInformationRule parent = systemRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System rule not found"));
            enforceReadable(normalizedRole, tenantId, parent.getTenantId(), "System rule not found");
            return conditionRepository.findByRuleId(id);
        });
    }

    public Mono<SystemRuleCloneResult> cloneSystemRule(String actor, String role, String scopeTenantId, Long id) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String targetTenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        if (targetTenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id is required for clone target");
        }
        return blockingDb.mono(() -> transactionTemplate.execute(status -> {
            SystemInformationRule source = systemRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System rule not found"));
            enforceReadable(normalizedRole, targetTenantId, source.getTenantId(), "System rule not found");

            OffsetDateTime now = OffsetDateTime.now();
            SystemInformationRule clone = new SystemInformationRule();
            clone.setId(null);
            clone.setRuleCode(generateSystemRuleCode());
            clone.setPriority(source.getPriority());
            clone.setVersion(source.getVersion());
            clone.setMatchMode(source.getMatchMode());
            clone.setComplianceAction(source.getComplianceAction());
            clone.setRiskScoreDelta(source.getRiskScoreDelta());
            clone.setRuleTag(source.getRuleTag());
            clone.setTenantId(targetTenantId);
            clone.setStatus(source.getStatus());
            clone.setSeverity(source.getSeverity());
            clone.setDescription(source.getDescription());
            clone.setDeviceType(source.getDeviceType());
            clone.setOsType(source.getOsType());
            clone.setOsName(source.getOsName());
            clone.setOsVersion(source.getOsVersion());
            clone.setTimeZone(source.getTimeZone());
            clone.setKernelVersion(source.getKernelVersion());
            clone.setEffectiveFrom(source.getEffectiveFrom() == null ? now : source.getEffectiveFrom());
            clone.setEffectiveTo(source.getEffectiveTo());
            clone.setDeleted(false);
            clone.setCreatedAt(now);
            clone.setCreatedBy(a);
            clone.setModifiedAt(now);
            clone.setModifiedBy(a);
            validateRuleWindow(clone.getEffectiveFrom(), clone.getEffectiveTo(), "system rule");
            SystemInformationRule persistedRule = systemRuleRepository.save(clone);

            int clonedConditions = 0;
            List<SystemInformationRuleCondition> sourceConditions = conditionRepository.findByRuleId(source.getId());
            for (SystemInformationRuleCondition sourceCondition : sourceConditions) {
                if (sourceCondition.isDeleted()) {
                    continue;
                }
                SystemInformationRuleCondition conditionClone = new SystemInformationRuleCondition();
                conditionClone.setId(null);
                conditionClone.setSystemInformationRuleId(persistedRule.getId());
                conditionClone.setConditionGroup(sourceCondition.getConditionGroup());
                conditionClone.setFieldName(sourceCondition.getFieldName());
                conditionClone.setOperator(sourceCondition.getOperator());
                conditionClone.setValueText(sourceCondition.getValueText());
                conditionClone.setValueNumeric(sourceCondition.getValueNumeric());
                conditionClone.setValueBoolean(sourceCondition.getValueBoolean());
                conditionClone.setValueJson(sourceCondition.getValueJson());
                conditionClone.setWeight(sourceCondition.getWeight());
                conditionClone.setStatus(sourceCondition.getStatus());
                conditionClone.setDeleted(false);
                conditionClone.setCreatedAt(now);
                conditionClone.setCreatedBy(a);
                conditionClone.setModifiedAt(now);
                conditionClone.setModifiedBy(a);
                conditionRepository.save(conditionClone);
                clonedConditions++;
            }

            recordAudit("SYSTEM_RULE", "CLONE", a, persistedRule.getTenantId(), persistedRule.getId(), source, persistedRule, null);
            return new SystemRuleCloneResult(persistedRule, clonedConditions);
        }));
    }

    public Mono<SystemInformationRule> createSystemRule(String actor, Mono<SystemInformationRule> request) {
        return createSystemRule(actor, ROLE_PRODUCT_ADMIN, null, request);
    }

    public Mono<SystemInformationRule> createSystemRule(String actor,
                                                        String role,
                                                        String scopeTenantId,
                                                        Mono<SystemInformationRule> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setRuleCode(resolveSystemRuleCodeForCreate(body.getRuleCode()));
            body.setTenantId(tenantId);
            body.setDeleted(false);
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(now);
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("ACTIVE");
            }
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "system rule");
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            SystemInformationRule persisted = systemRuleRepository.save(body);
            recordAudit("SYSTEM_RULE", "CREATE", a, persisted.getTenantId(), persisted.getId(), null, persisted, null);
            return persisted;
        }));
    }

    public Mono<SystemInformationRule> updateSystemRule(String actor, Long id, Mono<SystemInformationRule> request) {
        return updateSystemRule(actor, ROLE_PRODUCT_ADMIN, null, id, request);
    }

    public Mono<SystemInformationRule> updateSystemRule(String actor,
                                                        String role,
                                                        String scopeTenantId,
                                                        Long id,
                                                        Mono<SystemInformationRule> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            SystemInformationRule existing = systemRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this system rule");
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(id);
            body.setRuleCode(resolveSystemRuleCodeForUpdate(existing.getRuleCode(), body.getRuleCode()));
            body.setTenantId(existing.getTenantId());
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(existing.getEffectiveFrom());
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus(existing.getStatus());
            }
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "system rule");
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            SystemInformationRule persisted = systemRuleRepository.save(body);
            recordAudit("SYSTEM_RULE", "UPDATE", a, persisted.getTenantId(), persisted.getId(), existing, persisted, null);
            return persisted;
        }));
    }

    public Mono<Void> deleteSystemRule(String actor, Long id) {
        return deleteSystemRule(actor, ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<Void> deleteSystemRule(String actor, String role, String scopeTenantId, Long id) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> {
            SystemInformationRule existing = systemRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this system rule");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            systemRuleRepository.save(existing);
            recordAudit("SYSTEM_RULE", "DELETE", a, existing.getTenantId(), existing.getId(), existing, null, null);
        });
    }

    public Mono<SystemInformationRuleCondition> createSystemRuleCondition(String actor, Long ruleId, Mono<SystemInformationRuleCondition> request) {
        return createSystemRuleCondition(actor, ROLE_PRODUCT_ADMIN, null, ruleId, request);
    }

    public Mono<SystemInformationRuleCondition> createSystemRuleCondition(String actor,
                                                                          String role,
                                                                          String scopeTenantId,
                                                                          Long ruleId,
                                                                          Mono<SystemInformationRuleCondition> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            SystemInformationRule parent = systemRuleRepository.findById(ruleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System rule not found"));
            enforceWritable(normalizedRole, tenantId, parent.getTenantId(), "You cannot modify this system rule");
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setSystemInformationRuleId(ruleId);
            body.setDeleted(false);
            if (body.getConditionGroup() == null) {
                body.setConditionGroup((short) 1);
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("ACTIVE");
            }
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            return conditionRepository.save(body);
        }));
    }

    public Mono<SystemInformationRuleCondition> updateSystemRuleCondition(String actor,
                                                                          Long ruleId,
                                                                          Long conditionId,
                                                                          Mono<SystemInformationRuleCondition> request) {
        return updateSystemRuleCondition(actor, ROLE_PRODUCT_ADMIN, null, ruleId, conditionId, request);
    }

    public Mono<SystemInformationRuleCondition> updateSystemRuleCondition(String actor,
                                                                          String role,
                                                                          String scopeTenantId,
                                                                          Long ruleId,
                                                                          Long conditionId,
                                                                          Mono<SystemInformationRuleCondition> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            SystemInformationRule parent = systemRuleRepository.findById(ruleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System rule not found"));
            enforceWritable(normalizedRole, tenantId, parent.getTenantId(), "You cannot modify this system rule");

            SystemInformationRuleCondition existing = conditionRepository.findById(conditionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (existing.getSystemInformationRuleId() != null && !existing.getSystemInformationRuleId().equals(ruleId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(conditionId);
            body.setSystemInformationRuleId(ruleId);
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            if (body.getConditionGroup() == null) {
                body.setConditionGroup(existing.getConditionGroup());
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus(existing.getStatus());
            }
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            return conditionRepository.save(body);
        }));
    }

    public Mono<Void> deleteSystemRuleCondition(String actor, Long ruleId, Long conditionId) {
        return deleteSystemRuleCondition(actor, ROLE_PRODUCT_ADMIN, null, ruleId, conditionId);
    }

    public Mono<Void> deleteSystemRuleCondition(String actor,
                                                String role,
                                                String scopeTenantId,
                                                Long ruleId,
                                                Long conditionId) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> {
            SystemInformationRule parent = systemRuleRepository.findById(ruleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System rule not found"));
            enforceWritable(normalizedRole, tenantId, parent.getTenantId(), "You cannot modify this system rule");

            SystemInformationRuleCondition existing = conditionRepository.findById(conditionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (existing.getSystemInformationRuleId() != null && !existing.getSystemInformationRuleId().equals(ruleId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            conditionRepository.save(existing);
        });
    }

    public Flux<RejectApplication> listRejectApps(String osType, String status, int page, int size) {
        return listRejectApps(ROLE_PRODUCT_ADMIN, null, osType, status, page, size);
    }

    public Flux<RejectApplication> listRejectApps(String role,
                                                  String scopeTenantId,
                                                  String osType,
                                                  String status,
                                                  int page,
                                                  int size) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> rejectApplicationRepository.findPaged(tenantId, osType, status, limit, offset));
    }

    public Mono<RejectApplication> getRejectApp(Long id) {
        return getRejectApp(ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<RejectApplication> getRejectApp(String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.mono(() -> {
            RejectApplication app = rejectApplicationRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reject app not found"));
            enforceReadable(normalizedRole, tenantId, app.getTenantId(), "Reject app not found");
            return app;
        });
    }

    public Mono<RejectApplication> createRejectApp(String actor, Mono<RejectApplication> request) {
        return createRejectApp(actor, ROLE_PRODUCT_ADMIN, null, request);
    }

    public Mono<RejectApplication> createRejectApp(String actor,
                                                   String role,
                                                   String scopeTenantId,
                                                   Mono<RejectApplication> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setTenantId(tenantId);
            body.setDeleted(false);
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(now);
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("ACTIVE");
            }
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "reject-app policy");
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            RejectApplication persisted = rejectApplicationRepository.save(body);
            recordAudit("REJECT_APPLICATION", "CREATE", a, persisted.getTenantId(), persisted.getId(), null, persisted, null);
            return persisted;
        }));
    }

    public Mono<RejectApplication> updateRejectApp(String actor, Long id, Mono<RejectApplication> request) {
        return updateRejectApp(actor, ROLE_PRODUCT_ADMIN, null, id, request);
    }

    public Mono<RejectApplication> updateRejectApp(String actor,
                                                   String role,
                                                   String scopeTenantId,
                                                   Long id,
                                                   Mono<RejectApplication> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            RejectApplication existing = rejectApplicationRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this reject-app policy");
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(id);
            body.setTenantId(existing.getTenantId());
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(existing.getEffectiveFrom());
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus(existing.getStatus());
            }
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "reject-app policy");
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            RejectApplication persisted = rejectApplicationRepository.save(body);
            recordAudit("REJECT_APPLICATION", "UPDATE", a, persisted.getTenantId(), persisted.getId(), existing, persisted, null);
            return persisted;
        }));
    }

    public Mono<Void> deleteRejectApp(String actor, Long id) {
        return deleteRejectApp(actor, ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<Void> deleteRejectApp(String actor, String role, String scopeTenantId, Long id) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> {
            RejectApplication existing = rejectApplicationRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this reject-app policy");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            rejectApplicationRepository.save(existing);
            recordAudit("REJECT_APPLICATION", "DELETE", a, existing.getTenantId(), existing.getId(), existing, null, null);
        });
    }

    public Flux<TrustScorePolicy> listTrustScorePolicies(String status, int page, int size) {
        return listTrustScorePolicies(ROLE_PRODUCT_ADMIN, null, status, page, size);
    }

    public Flux<TrustScorePolicy> listTrustScorePolicies(String role, String scopeTenantId, String status, int page, int size) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> trustScorePolicyRepository.findPaged(tenantId, status, limit, offset));
    }

    public Mono<TrustScorePolicy> getTrustScorePolicy(Long id) {
        return getTrustScorePolicy(ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<TrustScorePolicy> getTrustScorePolicy(String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.mono(() -> {
            TrustScorePolicy policy = trustScorePolicyRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trust score policy not found"));
            enforceReadable(normalizedRole, tenantId, policy.getTenantId(), "Trust score policy not found");
            return policy;
        });
    }

    public Mono<TrustScorePolicy> createTrustScorePolicy(String actor, Mono<TrustScorePolicy> request) {
        return createTrustScorePolicy(actor, ROLE_PRODUCT_ADMIN, null, request);
    }

    public Mono<TrustScorePolicy> createTrustScorePolicy(String actor,
                                                         String role,
                                                         String scopeTenantId,
                                                         Mono<TrustScorePolicy> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setTenantId(tenantId);
            body.setDeleted(false);
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(now);
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("ACTIVE");
            }
            if (body.getWeight() == null) {
                body.setWeight(1.0);
            }
            validateTrustScorePolicySemantics(body, tenantId, null);
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            TrustScorePolicy persisted = trustScorePolicyRepository.save(body);
            recordAudit("TRUST_SCORE_POLICY", "CREATE", a, persisted.getTenantId(), persisted.getId(), null, persisted, null);
            return persisted;
        }));
    }

    public Mono<TrustScorePolicy> updateTrustScorePolicy(String actor, Long id, Mono<TrustScorePolicy> request) {
        return updateTrustScorePolicy(actor, ROLE_PRODUCT_ADMIN, null, id, request);
    }

    public Mono<TrustScorePolicy> updateTrustScorePolicy(String actor,
                                                         String role,
                                                         String scopeTenantId,
                                                         Long id,
                                                         Mono<TrustScorePolicy> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            TrustScorePolicy existing = trustScorePolicyRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this trust-score policy");
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(id);
            body.setTenantId(existing.getTenantId());
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(existing.getEffectiveFrom());
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus(existing.getStatus());
            }
            if (body.getWeight() == null) {
                body.setWeight(existing.getWeight());
            }
            validateTrustScorePolicySemantics(body, existing.getTenantId(), id);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            TrustScorePolicy persisted = trustScorePolicyRepository.save(body);
            recordAudit("TRUST_SCORE_POLICY", "UPDATE", a, persisted.getTenantId(), persisted.getId(), existing, persisted, null);
            return persisted;
        }));
    }

    public Mono<Void> deleteTrustScorePolicy(String actor, Long id) {
        return deleteTrustScorePolicy(actor, ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<Void> deleteTrustScorePolicy(String actor, String role, String scopeTenantId, Long id) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> {
            TrustScorePolicy existing = trustScorePolicyRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this trust-score policy");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            trustScorePolicyRepository.save(existing);
            recordAudit("TRUST_SCORE_POLICY", "DELETE", a, existing.getTenantId(), existing.getId(), existing, null, null);
        });
    }

    public Flux<TrustScoreDecisionPolicy> listTrustDecisionPolicies(String status, int page, int size) {
        return listTrustDecisionPolicies(ROLE_PRODUCT_ADMIN, null, status, page, size);
    }

    public Flux<TrustScoreDecisionPolicy> listTrustDecisionPolicies(String role, String scopeTenantId, String status, int page, int size) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> trustScoreDecisionPolicyRepository.findPaged(tenantId, status, limit, offset));
    }

    public Mono<TrustScoreDecisionPolicy> getTrustDecisionPolicy(Long id) {
        return getTrustDecisionPolicy(ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<TrustScoreDecisionPolicy> getTrustDecisionPolicy(String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.mono(() -> {
            TrustScoreDecisionPolicy policy = trustScoreDecisionPolicyRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trust decision policy not found"));
            enforceReadable(normalizedRole, tenantId, policy.getTenantId(), "Trust decision policy not found");
            return policy;
        });
    }

    public Mono<TrustScoreDecisionPolicy> createTrustDecisionPolicy(String actor, Mono<TrustScoreDecisionPolicy> request) {
        return createTrustDecisionPolicy(actor, ROLE_PRODUCT_ADMIN, null, request);
    }

    public Mono<TrustScoreDecisionPolicy> createTrustDecisionPolicy(String actor,
                                                                    String role,
                                                                    String scopeTenantId,
                                                                    Mono<TrustScoreDecisionPolicy> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setTenantId(tenantId);
            body.setDeleted(false);
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(now);
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("ACTIVE");
            }
            validateTrustDecisionPolicySemantics(body, tenantId, null);
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            TrustScoreDecisionPolicy persisted = trustScoreDecisionPolicyRepository.save(body);
            recordAudit("TRUST_DECISION_POLICY", "CREATE", a, persisted.getTenantId(), persisted.getId(), null, persisted, null);
            return persisted;
        }));
    }

    public Mono<TrustScoreDecisionPolicy> updateTrustDecisionPolicy(String actor, Long id, Mono<TrustScoreDecisionPolicy> request) {
        return updateTrustDecisionPolicy(actor, ROLE_PRODUCT_ADMIN, null, id, request);
    }

    public Mono<TrustScoreDecisionPolicy> updateTrustDecisionPolicy(String actor,
                                                                    String role,
                                                                    String scopeTenantId,
                                                                    Long id,
                                                                    Mono<TrustScoreDecisionPolicy> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            TrustScoreDecisionPolicy existing = trustScoreDecisionPolicyRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this trust-decision policy");
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(id);
            body.setTenantId(existing.getTenantId());
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(existing.getEffectiveFrom());
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus(existing.getStatus());
            }
            validateTrustDecisionPolicySemantics(body, existing.getTenantId(), id);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            TrustScoreDecisionPolicy persisted = trustScoreDecisionPolicyRepository.save(body);
            recordAudit("TRUST_DECISION_POLICY", "UPDATE", a, persisted.getTenantId(), persisted.getId(), existing, persisted, null);
            return persisted;
        }));
    }

    public Mono<Void> deleteTrustDecisionPolicy(String actor, Long id) {
        return deleteTrustDecisionPolicy(actor, ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<Void> deleteTrustDecisionPolicy(String actor, String role, String scopeTenantId, Long id) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> {
            TrustScoreDecisionPolicy existing = trustScoreDecisionPolicyRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this trust-decision policy");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            trustScoreDecisionPolicyRepository.save(existing);
            recordAudit("TRUST_DECISION_POLICY", "DELETE", a, existing.getTenantId(), existing.getId(), existing, null, null);
        });
    }

    public Flux<RemediationRule> listRemediationRules(String status, int page, int size) {
        return listRemediationRules(ROLE_PRODUCT_ADMIN, null, status, page, size);
    }

    public Flux<RemediationRule> listRemediationRules(String role, String scopeTenantId, String status, int page, int size) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> remediationRuleRepository.findPaged(tenantId, status, limit, offset));
    }

    public Mono<RemediationRule> getRemediationRule(Long id) {
        return getRemediationRule(ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<RemediationRule> getRemediationRule(String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.mono(() -> {
            RemediationRule rule = remediationRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remediation rule not found"));
            enforceReadable(normalizedRole, tenantId, rule.getTenantId(), "Remediation rule not found");
            return rule;
        });
    }

    public Mono<RemediationRule> createRemediationRule(String actor, Mono<RemediationRule> request) {
        return createRemediationRule(actor, ROLE_PRODUCT_ADMIN, null, request);
    }

    public Mono<RemediationRule> createRemediationRule(String actor,
                                                       String role,
                                                       String scopeTenantId,
                                                       Mono<RemediationRule> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setRemediationCode(resolveRemediationCodeForCreate(
                    body.getRemediationCode(),
                    body.getRemediationType(),
                    tenantId
            ));
            body.setTenantId(tenantId);
            body.setDeleted(false);
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(now);
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("ACTIVE");
            }
            if (body.getPriority() == null) {
                body.setPriority((short) 100);
            }
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "remediation rule");
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            RemediationRule persisted = remediationRuleRepository.save(body);
            recordAudit("REMEDIATION_RULE", "CREATE", a, persisted.getTenantId(), persisted.getId(), null, persisted, null);
            return persisted;
        }));
    }

    public Mono<RemediationRule> updateRemediationRule(String actor, Long id, Mono<RemediationRule> request) {
        return updateRemediationRule(actor, ROLE_PRODUCT_ADMIN, null, id, request);
    }

    public Mono<RemediationRule> updateRemediationRule(String actor,
                                                       String role,
                                                       String scopeTenantId,
                                                       Long id,
                                                       Mono<RemediationRule> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            RemediationRule existing = remediationRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this remediation rule");
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(id);
            body.setRemediationCode(resolveRemediationCodeForUpdate(
                    existing.getRemediationCode(),
                    body.getRemediationCode(),
                    body.getRemediationType(),
                    existing.getTenantId()
            ));
            body.setTenantId(existing.getTenantId());
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(existing.getEffectiveFrom());
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus(existing.getStatus());
            }
            if (body.getPriority() == null) {
                body.setPriority(existing.getPriority());
            }
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "remediation rule");
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            RemediationRule persisted = remediationRuleRepository.save(body);
            recordAudit("REMEDIATION_RULE", "UPDATE", a, persisted.getTenantId(), persisted.getId(), existing, persisted, null);
            return persisted;
        }));
    }

    public Mono<Void> deleteRemediationRule(String actor, Long id) {
        return deleteRemediationRule(actor, ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<Void> deleteRemediationRule(String actor, String role, String scopeTenantId, Long id) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> {
            RemediationRule existing = remediationRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this remediation rule");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            remediationRuleRepository.save(existing);
            recordAudit("REMEDIATION_RULE", "DELETE", a, existing.getTenantId(), existing.getId(), existing, null, null);
        });
    }

    public Flux<RuleRemediationMapping> listRuleRemediationMappings(String sourceType, int page, int size) {
        return listRuleRemediationMappings(ROLE_PRODUCT_ADMIN, null, sourceType, page, size);
    }

    public Flux<RuleRemediationMapping> listRuleRemediationMappings(String role,
                                                                    String scopeTenantId,
                                                                    String sourceType,
                                                                    int page,
                                                                    int size) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> ruleRemediationMappingRepository.findPaged(tenantId, sourceType, limit, offset));
    }

    public Mono<RuleRemediationMapping> getRuleRemediationMapping(Long id) {
        return getRuleRemediationMapping(ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<RuleRemediationMapping> getRuleRemediationMapping(String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.mono(() -> {
            RuleRemediationMapping mapping = ruleRemediationMappingRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule remediation mapping not found"));
            enforceReadable(normalizedRole, tenantId, mapping.getTenantId(), "Rule remediation mapping not found");
            return mapping;
        });
    }

    public Mono<RuleRemediationMapping> createRuleRemediationMapping(String actor, Mono<RuleRemediationMapping> request) {
        return createRuleRemediationMapping(actor, ROLE_PRODUCT_ADMIN, null, request);
    }

    public Mono<RuleRemediationMapping> createRuleRemediationMapping(String actor,
                                                                     String role,
                                                                     String scopeTenantId,
                                                                     Mono<RuleRemediationMapping> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setTenantId(tenantId);
            body.setDeleted(false);
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(now);
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("ACTIVE");
            }
            if (body.getEnforceMode() == null || body.getEnforceMode().isBlank()) {
                body.setEnforceMode("ADVISORY");
            }
            if (body.getRankOrder() == null) {
                body.setRankOrder((short) 1);
            }
            validateMappingReferences(normalizedRole, tenantId, body, null);
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "rule-remediation mapping");
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            RuleRemediationMapping persisted = ruleRemediationMappingRepository.save(body);
            recordAudit("RULE_REMEDIATION_MAPPING", "CREATE", a, persisted.getTenantId(), persisted.getId(), null, persisted, null);
            return persisted;
        }));
    }

    public Mono<RuleRemediationMapping> updateRuleRemediationMapping(String actor, Long id, Mono<RuleRemediationMapping> request) {
        return updateRuleRemediationMapping(actor, ROLE_PRODUCT_ADMIN, null, id, request);
    }

    public Mono<RuleRemediationMapping> updateRuleRemediationMapping(String actor,
                                                                     String role,
                                                                     String scopeTenantId,
                                                                     Long id,
                                                                     Mono<RuleRemediationMapping> request) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> {
            RuleRemediationMapping existing = ruleRemediationMappingRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this rule-remediation mapping");
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(id);
            body.setTenantId(existing.getTenantId());
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            if (body.getEffectiveFrom() == null) {
                body.setEffectiveFrom(existing.getEffectiveFrom());
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus(existing.getStatus());
            }
            if (body.getEnforceMode() == null || body.getEnforceMode().isBlank()) {
                body.setEnforceMode(existing.getEnforceMode());
            }
            if (body.getRankOrder() == null) {
                body.setRankOrder(existing.getRankOrder());
            }
            validateMappingReferences(normalizedRole, tenantId, body, id);
            validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "rule-remediation mapping");
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            RuleRemediationMapping persisted = ruleRemediationMappingRepository.save(body);
            recordAudit("RULE_REMEDIATION_MAPPING", "UPDATE", a, persisted.getTenantId(), persisted.getId(), existing, persisted, null);
            return persisted;
        }));
    }

    public Mono<Void> deleteRuleRemediationMapping(String actor, Long id) {
        return deleteRuleRemediationMapping(actor, ROLE_PRODUCT_ADMIN, null, id);
    }

    public Mono<Void> deleteRuleRemediationMapping(String actor, String role, String scopeTenantId, Long id) {
        String a = normalizeActor(actor);
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> {
            RuleRemediationMapping existing = ruleRemediationMappingRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this rule-remediation mapping");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            ruleRemediationMappingRepository.save(existing);
            recordAudit("RULE_REMEDIATION_MAPPING", "DELETE", a, existing.getTenantId(), existing.getId(), existing, null, null);
        });
    }

    private void validateMappingReferences(String role,
                                           String scopeTenantId,
                                           RuleRemediationMapping body,
                                           Long existingId) {
        validateMappingSemantics(body);

        if (body.getSystemInformationRuleId() != null) {
            SystemInformationRule rule = systemRuleRepository.findById(body.getSystemInformationRuleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "system_information_rule_id not found"));
            enforceReadable(role, scopeTenantId, rule.getTenantId(), "system_information_rule_id not found");
        }

        if (body.getRejectApplicationListId() != null) {
            RejectApplication reject = rejectApplicationRepository.findById(body.getRejectApplicationListId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "reject_application_list_id not found"));
            enforceReadable(role, scopeTenantId, reject.getTenantId(), "reject_application_list_id not found");
        }

        if (body.getTrustScorePolicyId() != null) {
            TrustScorePolicy policy = trustScorePolicyRepository.findById(body.getTrustScorePolicyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "trust_score_policy_id not found"));
            enforceReadable(role, scopeTenantId, policy.getTenantId(), "trust_score_policy_id not found");
        }

        if (body.getRemediationRuleId() != null) {
            RemediationRule remediationRule = remediationRuleRepository.findById(body.getRemediationRuleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "remediation_rule_id not found"));
            enforceReadable(role, scopeTenantId, remediationRule.getTenantId(), "remediation_rule_id not found");
        }

        List<RuleRemediationMapping> existingMappings = toList(ruleRemediationMappingRepository.findAll());
        for (RuleRemediationMapping existing : existingMappings) {
            if (existing.isDeleted()) {
                continue;
            }
            if (existingId != null && Objects.equals(existing.getId(), existingId)) {
                continue;
            }
            if (!sameTenant(scopeTenantId, existing.getTenantId())) {
                continue;
            }
            if (!Objects.equals(normalizeUpper(existing.getSourceType()), normalizeUpper(body.getSourceType()))) {
                continue;
            }
            if (!Objects.equals(existing.getSystemInformationRuleId(), body.getSystemInformationRuleId())) {
                continue;
            }
            if (!Objects.equals(existing.getRejectApplicationListId(), body.getRejectApplicationListId())) {
                continue;
            }
            if (!Objects.equals(existing.getTrustScorePolicyId(), body.getTrustScorePolicyId())) {
                continue;
            }
            if (!Objects.equals(normalizeUpper(existing.getDecisionAction()), normalizeUpper(body.getDecisionAction()))) {
                continue;
            }
            if (!Objects.equals(existing.getRemediationRuleId(), body.getRemediationRuleId())) {
                continue;
            }
            if (!Objects.equals(existing.getRankOrder(), body.getRankOrder())) {
                continue;
            }
            if (windowsOverlap(existing.getEffectiveFrom(), existing.getEffectiveTo(), body.getEffectiveFrom(), body.getEffectiveTo())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Overlapping mapping already exists for this source/remediation/rank");
            }
        }
    }

    private void validateMappingSemantics(RuleRemediationMapping body) {
        String sourceType = normalizeUpper(body.getSourceType());
        if (sourceType == null || !MAPPING_SOURCES.contains(sourceType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source_type is invalid");
        }

        int sourceRefCount = 0;
        sourceRefCount += body.getSystemInformationRuleId() != null ? 1 : 0;
        sourceRefCount += body.getRejectApplicationListId() != null ? 1 : 0;
        sourceRefCount += body.getTrustScorePolicyId() != null ? 1 : 0;
        sourceRefCount += normalizeUpper(body.getDecisionAction()) != null ? 1 : 0;

        if (sourceRefCount != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly one source reference must be set");
        }

        switch (sourceType) {
            case "SYSTEM_RULE" -> require(body.getSystemInformationRuleId() != null, "system_information_rule_id is required for SYSTEM_RULE");
            case "REJECT_APPLICATION" -> require(body.getRejectApplicationListId() != null, "reject_application_list_id is required for REJECT_APPLICATION");
            case "TRUST_POLICY" -> require(body.getTrustScorePolicyId() != null, "trust_score_policy_id is required for TRUST_POLICY");
            case "DECISION" -> {
                String decisionAction = normalizeUpper(body.getDecisionAction());
                require(decisionAction != null, "decision_action is required for DECISION");
                require(DECISION_ACTIONS.contains(decisionAction), "decision_action is invalid");
                body.setDecisionAction(decisionAction);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source_type is invalid");
        }

        if (!"DECISION".equals(sourceType) && body.getDecisionAction() != null && !body.getDecisionAction().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision_action must be empty unless source_type is DECISION");
        }
        if (body.getRemediationRuleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remediation_rule_id is required");
        }
    }

    private void validateTrustScorePolicySemantics(TrustScorePolicy body, String tenantId, Long existingId) {
        String sourceType = normalizeUpper(body.getSourceType());
        require(sourceType != null, "source_type is required");
        require(body.getSignalKey() != null && !body.getSignalKey().isBlank(), "signal_key is required");
        require(body.getScoreDelta() != null, "score_delta is required");
        require(body.getWeight() != null && body.getWeight() > 0.0, "weight must be greater than 0");
        validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "trust-score policy");

        if (!"ACTIVE".equalsIgnoreCase(defaultIfBlank(body.getStatus(), "ACTIVE"))) {
            return;
        }

        List<TrustScorePolicy> existingPolicies = toList(trustScorePolicyRepository.findAll());
        for (TrustScorePolicy existing : existingPolicies) {
            if (existing.isDeleted()) {
                continue;
            }
            if (!"ACTIVE".equalsIgnoreCase(defaultIfBlank(existing.getStatus(), "ACTIVE"))) {
                continue;
            }
            if (existingId != null && Objects.equals(existing.getId(), existingId)) {
                continue;
            }
            if (!sameTenant(tenantId, existing.getTenantId())) {
                continue;
            }
            if (!Objects.equals(normalizeUpper(existing.getSourceType()), sourceType)) {
                continue;
            }
            if (!equalsIgnoreCase(existing.getSignalKey(), body.getSignalKey())) {
                continue;
            }
            if (!Objects.equals(existing.getSeverity(), body.getSeverity())) {
                continue;
            }
            if (!equalsIgnoreCase(existing.getComplianceAction(), body.getComplianceAction())) {
                continue;
            }
            if (windowsOverlap(existing.getEffectiveFrom(), existing.getEffectiveTo(), body.getEffectiveFrom(), body.getEffectiveTo())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Overlapping trust-score policy exists for this signal/severity/action");
            }
        }
    }

    private void validateTrustDecisionPolicySemantics(TrustScoreDecisionPolicy body, String tenantId, Long existingId) {
        require(body.getScoreMin() != null, "score_min is required");
        require(body.getScoreMax() != null, "score_max is required");
        require(body.getScoreMin() >= 0 && body.getScoreMin() <= 100, "score_min must be between 0 and 100");
        require(body.getScoreMax() >= 0 && body.getScoreMax() <= 100, "score_max must be between 0 and 100");
        require(body.getScoreMin() <= body.getScoreMax(), "score_min must be <= score_max");
        String action = normalizeUpper(body.getDecisionAction());
        require(action != null && DECISION_ACTIONS.contains(action), "decision_action is invalid");
        body.setDecisionAction(action);
        validateRuleWindow(body.getEffectiveFrom(), body.getEffectiveTo(), "trust-decision policy");

        if (!"ACTIVE".equalsIgnoreCase(defaultIfBlank(body.getStatus(), "ACTIVE"))) {
            return;
        }

        List<TrustScoreDecisionPolicy> existingPolicies = toList(trustScoreDecisionPolicyRepository.findAll());
        for (TrustScoreDecisionPolicy existing : existingPolicies) {
            if (existing.isDeleted()) {
                continue;
            }
            if (!"ACTIVE".equalsIgnoreCase(defaultIfBlank(existing.getStatus(), "ACTIVE"))) {
                continue;
            }
            if (existingId != null && Objects.equals(existing.getId(), existingId)) {
                continue;
            }
            if (!sameTenant(tenantId, existing.getTenantId())) {
                continue;
            }
            if (windowsOverlap(existing.getEffectiveFrom(), existing.getEffectiveTo(), body.getEffectiveFrom(), body.getEffectiveTo())
                    && scoreRangesOverlap(existing.getScoreMin(), existing.getScoreMax(), body.getScoreMin(), body.getScoreMax())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Overlapping trust-decision score range exists for this effective window");
            }
        }
    }

    private boolean scoreRangesOverlap(Short minA, Short maxA, Short minB, Short maxB) {
        if (minA == null || maxA == null || minB == null || maxB == null) {
            return false;
        }
        return minA <= maxB && minB <= maxA;
    }

    private boolean windowsOverlap(OffsetDateTime fromA,
                                   OffsetDateTime toA,
                                   OffsetDateTime fromB,
                                   OffsetDateTime toB) {
        OffsetDateTime startA = fromA == null ? OffsetDateTime.MIN : fromA;
        OffsetDateTime endA = toA == null ? OffsetDateTime.MAX : toA;
        OffsetDateTime startB = fromB == null ? OffsetDateTime.MIN : fromB;
        OffsetDateTime endB = toB == null ? OffsetDateTime.MAX : toB;
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    private void validateRuleWindow(OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo, String policyType) {
        if (effectiveFrom != null && effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, policyType + " effective_to must be after effective_from");
        }
    }

    private boolean sameTenant(String expectedTenantId, String actualTenantId) {
        return Objects.equals(normalizeOptionalTenantId(expectedTenantId), normalizeOptionalTenantId(actualTenantId));
    }

    private String normalizeUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        if (right == null || right.isBlank()) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private <T> List<T> toList(Iterable<T> rows) {
        if (rows == null) {
            return List.of();
        }
        List<T> list = new ArrayList<>();
        rows.forEach(list::add);
        return list;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void recordAudit(String policyType,
                             String operation,
                             String actor,
                             String tenantId,
                             Long policyId,
                             Object before,
                             Object after,
                             String approvalTicket) {
        PolicyChangeAudit audit = new PolicyChangeAudit();
        audit.setPolicyType(policyType);
        audit.setPolicyId(policyId);
        audit.setOperation(operation);
        audit.setTenantId(normalizeOptionalTenantId(tenantId));
        audit.setActor(normalizeActor(actor));
        audit.setApprovalTicket(approvalTicket);
        audit.setBeforeStateJson(toAuditJson(before));
        audit.setAfterStateJson(toAuditJson(after));
        audit.setCreatedAt(OffsetDateTime.now());
        PolicyAuditMessage message = toAuditMessage(audit);
        try {
            policyAuditPublisher.publish(message);
            incrementAuditMetric(policyType, operation, tenantId, "success");
            recordGenericAuditEvent(audit, "SUCCESS");
            return;
        } catch (RuntimeException _) {
            // Best-effort fallback for environments where queue delivery is unavailable.
        }

        try {
            policyChangeAuditRepository.save(audit);
            incrementAuditMetric(policyType, operation, tenantId, "success");
            recordGenericAuditEvent(audit, "SUCCESS");
        } catch (RuntimeException ex) {
            incrementAuditMetric(policyType, operation, tenantId, "failure");
            recordGenericAuditEvent(audit, "FAILURE");
            throw ex;
        }
    }

    private void recordGenericAuditEvent(PolicyChangeAudit audit, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyType", audit.getPolicyType());
        metadata.put("policyId", audit.getPolicyId());
        metadata.put("operation", audit.getOperation());
        metadata.put("approvalTicket", audit.getApprovalTicket());
        metadata.put("beforeStateJson", audit.getBeforeStateJson());
        metadata.put("afterStateJson", audit.getAfterStateJson());

        auditEventService.recordBestEffort(
                "POLICY",
                "POLICY_CHANGE",
                audit.getOperation(),
                audit.getTenantId(),
                audit.getActor(),
                audit.getPolicyType(),
                audit.getPolicyId() == null ? null : String.valueOf(audit.getPolicyId()),
                status,
                metadata
        );
    }

    private void incrementAuditMetric(String policyType, String operation, String tenantId, String outcome) {
        meterRegistry.counter(
                "mdm.policy.audit.events",
                "policy_type", normalizeMetricTag(policyType),
                "operation", normalizeMetricTag(operation),
                "scope", normalizeOptionalTenantId(tenantId) == null ? "global" : "tenant",
                "outcome", normalizeMetricTag(outcome)
        ).increment();
    }

    private PolicyAuditMessage toAuditMessage(PolicyChangeAudit audit) {
        PolicyAuditMessage message = new PolicyAuditMessage();
        message.setSchemaVersion(PolicyAuditMessage.CURRENT_SCHEMA_VERSION);
        message.setEventId(UUID.randomUUID().toString());
        message.setPolicyType(audit.getPolicyType());
        message.setPolicyId(audit.getPolicyId());
        message.setOperation(audit.getOperation());
        message.setTenantId(audit.getTenantId());
        message.setActor(audit.getActor());
        message.setApprovalTicket(audit.getApprovalTicket());
        message.setBeforeStateJson(audit.getBeforeStateJson());
        message.setAfterStateJson(audit.getAfterStateJson());
        message.setCreatedAt(audit.getCreatedAt());
        return message;
    }

    private String normalizeMetricTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
    }

    private String toAuditJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"serialization_error\":true}";
        }
    }

    private void enforceRoleScopeCompatibility(String role, String scopeTenantId) {
        if (isTenantScopedRole(role) && scopeTenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope is required");
        }
    }

    private String resolveCreateTenantId(String role, String scopeTenantId) {
        if (isProductAdmin(role)) {
            return scopeTenantId;
        }
        if (isTenantScopedRole(role)) {
            if (scopeTenantId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope is required");
            }
            return scopeTenantId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported user role");
    }

    private void enforceReadable(String role, String scopeTenantId, String recordTenantId, String notFoundMessage) {
        if (!canRead(role, scopeTenantId, recordTenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
    }

    private void enforceWritable(String role, String scopeTenantId, String recordTenantId, String forbiddenMessage) {
        String recordTenant = normalizeOptionalTenantId(recordTenantId);

        if (isProductAdmin(role)) {
            if (scopeTenantId == null) {
                if (recordTenant != null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
                }
                return;
            }
            if (recordTenant != null && !Objects.equals(scopeTenantId, recordTenant)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
            }
            return;
        }

        if (isTenantScopedRole(role)) {
            if (scopeTenantId == null || !Objects.equals(scopeTenantId, recordTenant)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported user role");
    }

    private boolean canRead(String role, String scopeTenantId, String recordTenantId) {
        String recordTenant = normalizeOptionalTenantId(recordTenantId);

        if (isProductAdmin(role)) {
            if (scopeTenantId == null) {
                return recordTenant == null;
            }
            return recordTenant == null || Objects.equals(scopeTenantId, recordTenant);
        }

        if (isTenantScopedRole(role)) {
            if (scopeTenantId == null) {
                return false;
            }
            return recordTenant == null || Objects.equals(scopeTenantId, recordTenant);
        }

        return false;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return ROLE_PRODUCT_ADMIN;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isProductAdmin(String role) {
        return ROLE_PRODUCT_ADMIN.equalsIgnoreCase(role);
    }

    private boolean isTenantScopedRole(String role) {
        return ROLE_TENANT_ADMIN.equalsIgnoreCase(role) || ROLE_TENANT_USER.equalsIgnoreCase(role);
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "ui" : actor;
    }

    private String resolveSystemRuleCodeForCreate(String requestedCode) {
        String normalized = normalizeOptionalCode(requestedCode);
        if (normalized != null) {
            return normalized;
        }
        return generateSystemRuleCode();
    }

    private String resolveSystemRuleCodeForUpdate(String existingCode, String requestedCode) {
        String persisted = normalizeOptionalCode(existingCode);
        if (persisted != null) {
            return persisted;
        }
        String normalizedRequested = normalizeOptionalCode(requestedCode);
        if (normalizedRequested != null) {
            return normalizedRequested;
        }
        return generateSystemRuleCode();
    }

    private String normalizeOptionalCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String generateSystemRuleCode() {
        String ts = OffsetDateTime.now(ZoneOffset.UTC).format(RULE_CODE_TS_FMT);
        String random = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return "SR-" + ts + "-" + random;
    }

    private String resolveRemediationCodeForCreate(String requestedCode, String remediationType, String tenantId) {
        String normalized = normalizeOptionalCode(requestedCode);
        if (normalized != null) {
            return normalized;
        }
        return generateRemediationCode(remediationType, tenantId);
    }

    private String resolveRemediationCodeForUpdate(String existingCode,
                                                   String requestedCode,
                                                   String remediationType,
                                                   String tenantId) {
        String persisted = normalizeOptionalCode(existingCode);
        if (persisted != null) {
            return persisted;
        }
        String normalizedRequested = normalizeOptionalCode(requestedCode);
        if (normalizedRequested != null) {
            return normalizedRequested;
        }
        return generateRemediationCode(remediationType, tenantId);
    }

    private String generateRemediationCode(String remediationType, String tenantId) {
        String scopeToken = sanitizeCodeToken(tenantId, "GLOBAL", 12);
        String typeToken = sanitizeCodeToken(remediationType, "GENERAL", 12);
        String ts = OffsetDateTime.now(ZoneOffset.UTC).format(RULE_CODE_TS_FMT);
        String random = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return "RM-" + scopeToken + "-" + typeToken + "-" + ts + "-" + random;
    }

    private String sanitizeCodeToken(String value, String fallback, int maxLength) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            return fallback;
        }
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        String normalized = tenantId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

}
