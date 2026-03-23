package com.e24online.mdm.service;

import com.e24online.mdm.domain.PolicyChangeAudit;
import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.RuleRemediationMapping;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.repository.PolicyChangeAuditRepository;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import com.e24online.mdm.service.messaging.PolicyAuditPublisher;
import com.e24online.mdm.web.dto.PolicyAuditMessage;
import com.e24online.mdm.web.dto.SimpleAppPolicyRequest;
import com.e24online.mdm.web.dto.SimpleAppPolicySummary;
import com.e24online.mdm.web.dto.SimpleDevicePolicyRequest;
import com.e24online.mdm.web.dto.SimpleDevicePolicySummary;
import com.e24online.mdm.web.dto.SimplePolicyStarterPackSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SimplePolicyService {

    private static final String ROLE_PRODUCT_ADMIN = "PRODUCT_ADMIN";
    private static final Set<String> TENANT_SCOPED_ROLES = Set.of("TENANT_ADMIN");
    private static final Set<String> STATUS_VALUES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> OS_TYPES = Set.of("ANDROID", "IOS", "WINDOWS", "MACOS", "LINUX", "CHROMEOS", "FREEBSD", "OPENBSD");
    private static final Set<String> DEVICE_TYPES = Set.of("PHONE", "TABLET", "LAPTOP", "DESKTOP", "IOT", "SERVER");
    private static final Set<String> CONDITION_OPERATORS = Set.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "REGEX", "EXISTS", "NOT_EXISTS");
    private static final Set<String> VALUE_TYPES = Set.of("TEXT", "NUMBER", "BOOLEAN");
    private static final DateTimeFormatter RULE_CODE_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int POLICY_FETCH_LIMIT = 2000;
    private static final String ANY_VERSION_SENTINEL = "999999.0.0";
    private static final List<String> NON_ANDROID_VIRTUAL_DEVICE_OS_TYPES = List.of(
            "WINDOWS",
            "MACOS",
            "LINUX",
            "CHROMEOS",
            "FREEBSD",
            "OPENBSD"
    );
    private static final List<StarterFixSpec> STARTER_FIXES = List.of(
            new StarterFixSpec(
                    "ROOT_ACCESS",
                    "Remove root access and rescan",
                    "Remove root tooling from the device, then run posture collection again.",
                    "USER_ACTION",
                    "ANDROID",
                    null,
                    """
                    {"version":1,"title":"Remove root access and rescan","summary":"Remove root tooling from the device, then run posture collection again.","steps":["Remove root tooling from the device.","Reboot the device to restore normal security state.","Open the agent and run posture collection again."]}
                    """,
                    (short) 10
            ),
            new StarterFixSpec(
                    "JAILBREAK_ACCESS",
                    "Remove jailbreak access and rescan",
                    "Remove jailbreak tooling from the device, then run posture collection again.",
                    "USER_ACTION",
                    "IOS",
                    null,
                    """
                    {"version":1,"title":"Remove jailbreak access and rescan","summary":"Remove jailbreak tooling from the device, then run posture collection again.","steps":["Remove jailbreak tooling or restore the device to a supported state.","Reboot the device so the integrity check can run cleanly.","Open the agent and run posture collection again."]}
                    """,
                    (short) 15
            ),
            new StarterFixSpec(
                    "USB_DEBUGGING",
                    "Turn off USB debugging",
                    "Disable USB debugging, then refresh the posture check.",
                    "USER_ACTION",
                    "ANDROID",
                    null,
                    """
                    {"version":1,"title":"Turn off USB debugging","summary":"Disable USB debugging, then refresh the posture check.","steps":["Open Developer Options on the device.","Turn off USB debugging.","Reconnect only after the policy check passes."]}
                    """,
                    (short) 20
            ),
            new StarterFixSpec(
                    "EMULATOR_USAGE",
                    "Stop using an emulated device",
                    "Use a real managed device instead of an emulator or virtual machine.",
                    "USER_ACTION",
                    "ANDROID",
                    null,
                    """
                    {"version":1,"title":"Stop using an emulated device","summary":"Use a real managed device instead of an emulator or virtual machine.","steps":["Move the user to a supported physical device.","Enroll the physical device into the platform.","Run posture collection again on the physical device."]}
                    """,
                    (short) 30
            ),
            new StarterFixSpec(
                    "NON_PHYSICAL_DEVICE",
                    "Move the workload to a supported physical device",
                    "Use a supported physical managed device instead of an emulator, simulator, or virtual machine, then collect posture again.",
                    "USER_ACTION",
                    null,
                    null,
                    """
                    {"version":1,"title":"Move the workload to a supported physical device","summary":"Use a supported physical managed device instead of an emulator, simulator, or virtual machine, then collect posture again.","steps":["Stop using the emulator, simulator, or virtual machine for the protected workflow.","Move the user or workload to a supported physical managed device.","Run posture collection again on the physical device."]}
                    """,
                    (short) 35
            ),
            new StarterFixSpec(
                    "REMOVE_REMOTE_ADMIN_TOOL",
                    "Remove the blocked remote admin tool",
                    "Uninstall the blocked remote admin application and run posture collection again.",
                    "APP_REMOVAL",
                    "WINDOWS",
                    null,
                    """
                    {"version":1,"title":"Remove the blocked remote admin tool","summary":"Uninstall the blocked remote admin application and run posture collection again.","steps":["Uninstall the blocked remote admin application from the device.","Confirm the application is no longer listed in installed programs.","Open the agent and run posture collection again."]}
                    """,
                    (short) 40
            ),
            new StarterFixSpec(
                    "UPDATE_OS_SUPPORTED",
                    "Update the device to a supported OS release",
                    "Move the device to a currently supported operating-system release and scan again.",
                    "OS_UPDATE",
                    null,
                    null,
                    """
                    {"version":1,"title":"Update the device to a supported OS release","summary":"Move the device to a currently supported operating-system release and scan again.","steps":["Review the supported OS baseline for the platform.","Apply the latest supported OS release for the device.","Run posture collection again after the update completes."]}
                    """,
                    (short) 50
            ),
            new StarterFixSpec(
                    "REPLACE_UNSUPPORTED_OS",
                    "Move the device off the unsupported OS release",
                    "Upgrade or replace the device because the current operating-system release is no longer supportable.",
                    "OS_UPDATE",
                    null,
                    null,
                    """
                    {"version":1,"title":"Move the device off the unsupported OS release","summary":"Upgrade or replace the device because the current operating-system release is no longer supportable.","steps":["Review the lifecycle baseline for the current operating-system release.","Upgrade the device to a supported release or replace the device.","Run posture collection again after the change."]}
                    """,
                    (short) 60
            )
    );
    private static final List<StarterDeviceRuleSpec> STARTER_DEVICE_RULES = buildStarterDeviceRules();
    private static final List<StarterAppRuleSpec> STARTER_APP_RULES = List.of(
            new StarterAppRuleSpec(
                    "Block AnyDesk remote admin tool",
                    "WINDOWS",
                    "AnyDesk",
                    "AnyDeskSoftwareGmbH.AnyDesk",
                    "AnyDesk Software GmbH",
                    null,
                    (short) 4,
                    "Remove the blocked remote admin tool"
            ),
            new StarterAppRuleSpec(
                    "Block TeamViewer remote admin tool",
                    "WINDOWS",
                    "TeamViewer",
                    null,
                    "TeamViewer Germany GmbH",
                    null,
                    (short) 4,
                    "Remove the blocked remote admin tool"
            )
    );
    private static final List<StarterTrustLevelSpec> STARTER_TRUST_LEVELS = List.of(
            new StarterTrustLevelSpec("Trusted devices", (short) 80, (short) 100, "ALLOW", false, "Device posture is within the trusted range."),
            new StarterTrustLevelSpec("Watch devices", (short) 60, (short) 79, "NOTIFY", false, "Device posture needs attention but does not require containment."),
            new StarterTrustLevelSpec("Restricted devices", (short) 40, (short) 59, "QUARANTINE", true, "Device posture requires restricted access until remediation completes."),
            new StarterTrustLevelSpec("Blocked devices", (short) 0, (short) 39, "BLOCK", true, "Device posture is outside the acceptable range and access is blocked.")
    );

    private final SystemInformationRuleRepository systemRuleRepository;
    private final SystemInformationRuleConditionRepository conditionRepository;
    private final RejectApplicationRepository rejectApplicationRepository;
    private final RuleRemediationMappingRepository ruleRemediationMappingRepository;
    private final RemediationRuleRepository remediationRuleRepository;
    private final TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    private final PolicyChangeAuditRepository policyChangeAuditRepository;
    private final PolicyAuditPublisher policyAuditPublisher;
    private final AuditEventService auditEventService;
    private final BlockingDb blockingDb;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private record StarterFixSpec(String codeStem,
                                  String title,
                                  String description,
                                  String remediationType,
                                  String osType,
                                  String deviceType,
                                  String instructionJson,
                                  short priority) {}

    private record StarterDeviceRuleSpec(String name,
                                         String description,
                                         String osType,
                                         String deviceType,
                                         short severity,
                                         String fieldName,
                                         String operator,
                                         String valueType,
                                         String valueText,
                                         Double valueNumeric,
                                         Boolean valueBoolean,
                                         String remediationTitle) {}

    private record StarterAppRuleSpec(String policyTag,
                                      String appOsType,
                                      String appName,
                                      String packageId,
                                      String publisher,
                                      String minAllowedVersion,
                                      short severity,
                                      String remediationTitle) {}

    private record StarterTrustLevelSpec(String label,
                                         short scoreMin,
                                         short scoreMax,
                                         String decisionAction,
                                         boolean remediationRequired,
                                         String responseMessage) {}

    public SimplePolicyService(SystemInformationRuleRepository systemRuleRepository,
                               SystemInformationRuleConditionRepository conditionRepository,
                               RejectApplicationRepository rejectApplicationRepository,
                               RuleRemediationMappingRepository ruleRemediationMappingRepository,
                               RemediationRuleRepository remediationRuleRepository,
                               TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository,
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
        this.ruleRemediationMappingRepository = ruleRemediationMappingRepository;
        this.remediationRuleRepository = remediationRuleRepository;
        this.trustScoreDecisionPolicyRepository = trustScoreDecisionPolicyRepository;
        this.policyChangeAuditRepository = policyChangeAuditRepository;
        this.policyAuditPublisher = policyAuditPublisher;
        this.auditEventService = auditEventService;
        this.blockingDb = blockingDb;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    private static List<StarterDeviceRuleSpec> buildStarterDeviceRules() {
        List<StarterDeviceRuleSpec> rules = new ArrayList<>();
        rules.add(new StarterDeviceRuleSpec(
                "Rooted Android device",
                "Block devices that report root access.",
                "ANDROID",
                null,
                (short) 5,
                "root_detected",
                "EQ",
                "BOOLEAN",
                null,
                null,
                Boolean.TRUE,
                "Remove root access and rescan"
        ));
        rules.add(new StarterDeviceRuleSpec(
                "Jailbroken iOS device",
                "Block iOS devices that report jailbreak access.",
                "IOS",
                null,
                (short) 5,
                "root_detected",
                "EQ",
                "BOOLEAN",
                null,
                null,
                Boolean.TRUE,
                "Remove jailbreak access and rescan"
        ));
        rules.add(new StarterDeviceRuleSpec(
                "USB debugging enabled",
                "Quarantine devices with USB debugging enabled.",
                "ANDROID",
                null,
                (short) 4,
                "usb_debugging_status",
                "EQ",
                "BOOLEAN",
                null,
                null,
                Boolean.TRUE,
                "Turn off USB debugging"
        ));
        rules.add(new StarterDeviceRuleSpec(
                "Running on emulator",
                "Quarantine Android devices that are reporting as emulated or virtualized.",
                "ANDROID",
                null,
                (short) 4,
                "running_on_emulator",
                "EQ",
                "BOOLEAN",
                null,
                null,
                Boolean.TRUE,
                "Stop using an emulated device"
        ));
        for (String osType : NON_ANDROID_VIRTUAL_DEVICE_OS_TYPES) {
            rules.add(new StarterDeviceRuleSpec(
                    "Virtualized " + displayOsName(osType) + " device",
                    "Quarantine " + displayOsName(osType) + " devices that report emulator, simulator, or virtualization usage.",
                    osType,
                    null,
                    (short) 4,
                    "running_on_emulator",
                    "EQ",
                    "BOOLEAN",
                    null,
                    null,
                    Boolean.TRUE,
                    "Move the workload to a supported physical device"
            ));
        }
        return List.copyOf(rules);
    }

    private static String displayOsName(String osType) {
        return switch (osType) {
            case "ANDROID" -> "Android";
            case "IOS" -> "iOS";
            case "WINDOWS" -> "Windows";
            case "MACOS" -> "macOS";
            case "LINUX" -> "Linux";
            case "CHROMEOS" -> "ChromeOS";
            case "FREEBSD" -> "FreeBSD";
            case "OPENBSD" -> "OpenBSD";
            default -> osType == null ? "device" : osType;
        };
    }

    public Flux<SimpleDevicePolicySummary> listDevicePolicies(String role, String scopeTenantId) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.flux(() -> {
            List<SystemInformationRule> rules = systemRuleRepository.findPaged(tenantId, null, POLICY_FETCH_LIMIT, 0);
            List<RuleRemediationMapping> mappings = ruleRemediationMappingRepository.findPaged(tenantId, "SYSTEM_RULE", POLICY_FETCH_LIMIT, 0);
            Map<Long, List<RuleRemediationMapping>> mappingsByRuleId = new HashMap<>();
            for (RuleRemediationMapping mapping : mappings) {
                if (mapping.getSystemInformationRuleId() == null) {
                    continue;
                }
                mappingsByRuleId.computeIfAbsent(mapping.getSystemInformationRuleId(), ignored -> new ArrayList<>()).add(mapping);
            }

            List<SimpleDevicePolicySummary> out = new ArrayList<>();
            for (SystemInformationRule rule : rules) {
                enforceReadable(normalizedRole, tenantId, rule.getTenantId(), "System rule not found");
                if (!isOwnedScope(tenantId, rule.getTenantId())) {
                    continue;
                }
                List<SystemInformationRuleCondition> conditions = conditionRepository.findByRuleId(rule.getId());
                out.add(toDeviceSummary(rule, conditions, mappingsByRuleId.getOrDefault(rule.getId(), List.of())));
            }
            out.sort(Comparator.comparing(SimpleDevicePolicySummary::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                    .thenComparing(SimpleDevicePolicySummary::getRuleCode, Comparator.nullsLast(String::compareToIgnoreCase)));
            return out;
        });
    }

    public Mono<SimpleDevicePolicySummary> createDevicePolicy(String actor,
                                                              String role,
                                                              String scopeTenantId,
                                                              Mono<SimpleDevicePolicyRequest> request) {
        String normalizedRole = normalizeRole(role);
        String normalizedActor = normalizeActor(actor);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            validateDeviceRequest(body);
            RemediationRule remediationRule = body.getRemediationRuleId() == null
                    ? null
                    : requireReadableRemediation(normalizedRole, tenantId, body.getRemediationRuleId());

            SystemInformationRule rule = new SystemInformationRule();
            rule.setRuleCode(generateSystemRuleCode());
            rule.setRuleTag(normalizeRequiredText(body.getName(), "name is required"));
            rule.setDescription(normalizeOptionalText(body.getDescription()));
            rule.setOsType(normalizeRequiredChoice(body.getOsType(), OS_TYPES, "os_type is invalid"));
            rule.setOsName(normalizeOptionalText(body.getOsName()));
            rule.setDeviceType(normalizeOptionalChoice(body.getDeviceType(), DEVICE_TYPES, "device_type is invalid"));
            rule.setSeverity(normalizeSeverity(body.getSeverity()));
            rule.setPriority(defaultPriority(rule.getSeverity()));
            rule.setVersion(1);
            rule.setMatchMode("ALL");
            rule.setComplianceAction(defaultComplianceAction(rule.getSeverity()));
            rule.setRiskScoreDelta(severityToScoreDelta(rule.getSeverity()));
            rule.setTenantId(tenantId);
            rule.setStatus(normalizeStatus(body.getStatus()));
            rule.setEffectiveFrom(now);
            rule.setDeleted(false);
            rule.setCreatedAt(now);
            rule.setCreatedBy(normalizedActor);
            rule.setModifiedAt(now);
            rule.setModifiedBy(normalizedActor);
            SystemInformationRule persistedRule = systemRuleRepository.save(rule);
            recordAudit("SYSTEM_RULE", "CREATE", normalizedActor, persistedRule.getTenantId(), persistedRule.getId(), null, persistedRule, null);

            SystemInformationRuleCondition condition = buildCondition(body, null, persistedRule.getId(), now, normalizedActor);
            SystemInformationRuleCondition persistedCondition = conditionRepository.save(condition);
            recordAudit("SYSTEM_RULE_CONDITION", "CREATE", normalizedActor, persistedRule.getTenantId(), persistedCondition.getId(), null, persistedCondition, null);

            List<RuleRemediationMapping> mappings = syncSystemRuleMappings(
                    normalizedActor,
                    tenantId,
                    persistedRule.getId(),
                    body.getRemediationRuleId(),
                    now,
                    rule.getStatus()
            );

            return toDeviceSummary(persistedRule, List.of(persistedCondition), mappings, remediationRule);
        })));
    }

    public Mono<SimpleDevicePolicySummary> updateDevicePolicy(String actor,
                                                              String role,
                                                              String scopeTenantId,
                                                              Long id,
                                                              Mono<SimpleDevicePolicyRequest> request) {
        String normalizedRole = normalizeRole(role);
        String normalizedActor = normalizeActor(actor);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> transactionTemplate.execute(status -> {
            validateDeviceRequest(body);
            SystemInformationRule existing = systemRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device policy not found"));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this device policy");

            List<SystemInformationRuleCondition> existingConditions = conditionRepository.findByRuleId(id);
            if (existingConditions.size() != 1 || hasJsonCondition(existingConditions.getFirst())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This device policy is advanced-only and must be edited from the advanced policy pages");
            }

            List<RuleRemediationMapping> existingMappings = loadSystemRuleMappings(tenantId, id);
            if (existingMappings.size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This device policy has multiple fix mappings and must be edited from the advanced policy pages");
            }

            RemediationRule remediationRule = body.getRemediationRuleId() == null
                    ? null
                    : requireReadableRemediation(normalizedRole, tenantId, body.getRemediationRuleId());
            OffsetDateTime now = OffsetDateTime.now();

            SystemInformationRule updated = new SystemInformationRule();
            updated.setId(existing.getId());
            updated.setRuleCode(existing.getRuleCode());
            updated.setRuleTag(normalizeRequiredText(body.getName(), "name is required"));
            updated.setDescription(normalizeOptionalText(body.getDescription()));
            updated.setOsType(normalizeRequiredChoice(body.getOsType(), OS_TYPES, "os_type is invalid"));
            updated.setOsName(normalizeOptionalText(body.getOsName()));
            updated.setDeviceType(normalizeOptionalChoice(body.getDeviceType(), DEVICE_TYPES, "device_type is invalid"));
            updated.setSeverity(normalizeSeverity(body.getSeverity()));
            updated.setPriority(defaultPriority(updated.getSeverity()));
            updated.setVersion(Math.max(1, safeInt(existing.getVersion(), 1)) + 1);
            updated.setMatchMode("ALL");
            updated.setComplianceAction(defaultComplianceAction(updated.getSeverity()));
            updated.setRiskScoreDelta(severityToScoreDelta(updated.getSeverity()));
            updated.setTenantId(existing.getTenantId());
            updated.setStatus(normalizeStatus(body.getStatus()));
            updated.setEffectiveFrom(existing.getEffectiveFrom() == null ? now : existing.getEffectiveFrom());
            updated.setEffectiveTo(existing.getEffectiveTo());
            updated.setDeleted(existing.isDeleted());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setCreatedBy(existing.getCreatedBy());
            updated.setModifiedAt(now);
            updated.setModifiedBy(normalizedActor);
            SystemInformationRule persistedRule = systemRuleRepository.save(updated);
            recordAudit("SYSTEM_RULE", "UPDATE", normalizedActor, persistedRule.getTenantId(), persistedRule.getId(), existing, persistedRule, null);

            SystemInformationRuleCondition existingCondition = existingConditions.getFirst();
            SystemInformationRuleCondition updatedCondition = buildCondition(body, existingCondition, persistedRule.getId(), now, normalizedActor);
            SystemInformationRuleCondition persistedCondition = conditionRepository.save(updatedCondition);
            recordAudit("SYSTEM_RULE_CONDITION", "UPDATE", normalizedActor, persistedRule.getTenantId(), persistedCondition.getId(), existingCondition, persistedCondition, null);

            List<RuleRemediationMapping> mappings = syncSystemRuleMappings(
                    normalizedActor,
                    persistedRule.getTenantId(),
                    persistedRule.getId(),
                    body.getRemediationRuleId(),
                    now,
                    persistedRule.getStatus()
            );

            return toDeviceSummary(persistedRule, List.of(persistedCondition), mappings, remediationRule);
        })));
    }

    public Mono<Void> deleteDevicePolicy(String actor, String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String normalizedActor = normalizeActor(actor);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> transactionTemplate.executeWithoutResult(status -> {
            SystemInformationRule existing = systemRuleRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device policy not found"));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this device policy");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(normalizedActor);
            systemRuleRepository.save(existing);
            recordAudit("SYSTEM_RULE", "DELETE", normalizedActor, existing.getTenantId(), existing.getId(), existing, null, null);

            for (SystemInformationRuleCondition condition : conditionRepository.findByRuleId(id)) {
                condition.setDeleted(true);
                condition.setModifiedAt(OffsetDateTime.now());
                condition.setModifiedBy(normalizedActor);
                conditionRepository.save(condition);
                recordAudit("SYSTEM_RULE_CONDITION", "DELETE", normalizedActor, existing.getTenantId(), condition.getId(), condition, null, null);
            }

            for (RuleRemediationMapping mapping : loadSystemRuleMappings(tenantId, id)) {
                if (mapping.isDeleted()) {
                    continue;
                }
                mapping.setDeleted(true);
                mapping.setModifiedAt(OffsetDateTime.now());
                mapping.setModifiedBy(normalizedActor);
                ruleRemediationMappingRepository.save(mapping);
                recordAudit("RULE_REMEDIATION_MAPPING", "DELETE", normalizedActor, mapping.getTenantId(), mapping.getId(), mapping, null, null);
            }
        }));
    }

    public Flux<SimpleAppPolicySummary> listAppPolicies(String role, String scopeTenantId) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.flux(() -> {
            List<RejectApplication> apps = rejectApplicationRepository.findPaged(tenantId, null, null, POLICY_FETCH_LIMIT, 0);
            List<RuleRemediationMapping> mappings = ruleRemediationMappingRepository.findPaged(tenantId, "REJECT_APPLICATION", POLICY_FETCH_LIMIT, 0);
            Map<Long, List<RuleRemediationMapping>> mappingsByRejectId = new HashMap<>();
            for (RuleRemediationMapping mapping : mappings) {
                if (mapping.getRejectApplicationListId() == null) {
                    continue;
                }
                mappingsByRejectId.computeIfAbsent(mapping.getRejectApplicationListId(), ignored -> new ArrayList<>()).add(mapping);
            }

            List<SimpleAppPolicySummary> out = new ArrayList<>();
            for (RejectApplication app : apps) {
                enforceReadable(normalizedRole, tenantId, app.getTenantId(), "Reject app not found");
                if (!isOwnedScope(tenantId, app.getTenantId())) {
                    continue;
                }
                out.add(toAppSummary(app, mappingsByRejectId.getOrDefault(app.getId(), List.of())));
            }
            out.sort(Comparator.comparing(SimpleAppPolicySummary::getPolicyTag, Comparator.nullsLast(String::compareToIgnoreCase))
                    .thenComparing(SimpleAppPolicySummary::getAppName, Comparator.nullsLast(String::compareToIgnoreCase)));
            return out;
        });
    }

    public Flux<TrustScoreDecisionPolicy> listTrustLevels(String role, String scopeTenantId) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.flux(() -> trustScoreDecisionPolicyRepository.findPaged(tenantId, null, POLICY_FETCH_LIMIT, 0).stream()
                .filter(policy -> isOwnedScope(tenantId, policy.getTenantId()))
                .sorted(Comparator.comparing(TrustScoreDecisionPolicy::getScoreMin, Comparator.nullsLast(Short::compareTo))
                        .thenComparing(TrustScoreDecisionPolicy::getScoreMax, Comparator.nullsLast(Short::compareTo))
                        .thenComparing(TrustScoreDecisionPolicy::getPolicyName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList());
    }

    public Flux<RemediationRule> listFixLibrary(String role, String scopeTenantId) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.flux(() -> remediationRuleRepository.findPaged(tenantId, null, POLICY_FETCH_LIMIT, 0).stream()
                .filter(rule -> isOwnedScope(tenantId, rule.getTenantId()))
                .sorted(Comparator.comparing(RemediationRule::getTitle, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(RemediationRule::getRemediationCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList());
    }

    public Flux<RemediationRule> listFixOptions(String role, String scopeTenantId) {
        String normalizedRole = normalizeRole(role);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.flux(() -> remediationRuleRepository.findPaged(tenantId, "ACTIVE", POLICY_FETCH_LIMIT, 0).stream()
                .filter(rule -> canRead(normalizedRole, tenantId, rule.getTenantId()))
                .sorted(Comparator
                        .comparing((RemediationRule rule) -> scopePriority(rule.getTenantId(), tenantId))
                        .thenComparing(RemediationRule::getTitle, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(RemediationRule::getRemediationCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList());
    }

    public Mono<SimpleAppPolicySummary> createAppPolicy(String actor,
                                                        String role,
                                                        String scopeTenantId,
                                                        Mono<SimpleAppPolicyRequest> request) {
        String normalizedRole = normalizeRole(role);
        String normalizedActor = normalizeActor(actor);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return request.flatMap(body -> blockingDb.mono(() -> transactionTemplate.execute(status -> {
            validateAppRequest(body);
            RemediationRule remediationRule = body.getRemediationRuleId() == null
                    ? null
                    : requireReadableRemediation(normalizedRole, tenantId, body.getRemediationRuleId());
            OffsetDateTime now = OffsetDateTime.now();

            RejectApplication app = new RejectApplication();
            app.setPolicyTag(normalizeRequiredText(body.getPolicyTag(), "policy_tag is required"));
            app.setSeverity(normalizeSeverity(body.getSeverity()));
            app.setAppName(normalizeRequiredText(body.getAppName(), "app_name is required"));
            app.setPublisher(normalizeOptionalText(body.getPublisher()));
            app.setPackageId(normalizeOptionalText(body.getPackageId()));
            app.setAppOsType(normalizeRequiredChoice(body.getAppOsType(), OS_TYPES, "app_os_type is invalid"));
            app.setMinAllowedVersion(normalizeAppMinAllowedVersion(body.getMinAllowedVersion()));
            app.setTenantId(tenantId);
            app.setStatus(normalizeStatus(body.getStatus()));
            app.setEffectiveFrom(now);
            app.setDeleted(false);
            app.setCreatedAt(now);
            app.setCreatedBy(normalizedActor);
            app.setModifiedAt(now);
            app.setModifiedBy(normalizedActor);
            RejectApplication persisted = rejectApplicationRepository.save(app);
            recordAudit("REJECT_APPLICATION", "CREATE", normalizedActor, persisted.getTenantId(), persisted.getId(), null, persisted, null);

            List<RuleRemediationMapping> mappings = syncRejectMappings(
                    normalizedActor,
                    tenantId,
                    persisted.getId(),
                    body.getRemediationRuleId(),
                    now,
                    persisted.getStatus()
            );

            return toAppSummary(persisted, mappings, remediationRule);
        })));
    }

    public Mono<SimpleAppPolicySummary> updateAppPolicy(String actor,
                                                        String role,
                                                        String scopeTenantId,
                                                        Long id,
                                                        Mono<SimpleAppPolicyRequest> request) {
        String normalizedRole = normalizeRole(role);
        String normalizedActor = normalizeActor(actor);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return request.flatMap(body -> blockingDb.mono(() -> transactionTemplate.execute(status -> {
            validateAppRequest(body);
            RejectApplication existing = rejectApplicationRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App policy not found"));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot modify this app policy");

            List<RuleRemediationMapping> existingMappings = loadRejectMappings(tenantId, id);
            if (existingMappings.size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This app policy has multiple fix mappings and must be edited from the advanced policy pages");
            }

            RemediationRule remediationRule = body.getRemediationRuleId() == null
                    ? null
                    : requireReadableRemediation(normalizedRole, tenantId, body.getRemediationRuleId());
            OffsetDateTime now = OffsetDateTime.now();

            RejectApplication updated = new RejectApplication();
            updated.setId(existing.getId());
            updated.setPolicyTag(normalizeRequiredText(body.getPolicyTag(), "policy_tag is required"));
            updated.setSeverity(normalizeSeverity(body.getSeverity()));
            updated.setAppName(normalizeRequiredText(body.getAppName(), "app_name is required"));
            updated.setPublisher(normalizeOptionalText(body.getPublisher()));
            updated.setPackageId(normalizeOptionalText(body.getPackageId()));
            updated.setAppOsType(normalizeRequiredChoice(body.getAppOsType(), OS_TYPES, "app_os_type is invalid"));
            updated.setMinAllowedVersion(normalizeAppMinAllowedVersion(body.getMinAllowedVersion()));
            updated.setTenantId(existing.getTenantId());
            updated.setStatus(normalizeStatus(body.getStatus()));
            updated.setEffectiveFrom(existing.getEffectiveFrom() == null ? now : existing.getEffectiveFrom());
            updated.setEffectiveTo(existing.getEffectiveTo());
            updated.setDeleted(existing.isDeleted());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setCreatedBy(existing.getCreatedBy());
            updated.setModifiedAt(now);
            updated.setModifiedBy(normalizedActor);
            RejectApplication persisted = rejectApplicationRepository.save(updated);
            recordAudit("REJECT_APPLICATION", "UPDATE", normalizedActor, persisted.getTenantId(), persisted.getId(), existing, persisted, null);

            List<RuleRemediationMapping> mappings = syncRejectMappings(
                    normalizedActor,
                    persisted.getTenantId(),
                    persisted.getId(),
                    body.getRemediationRuleId(),
                    now,
                    persisted.getStatus()
            );

            return toAppSummary(persisted, mappings, remediationRule);
        })));
    }

    public Mono<Void> deleteAppPolicy(String actor, String role, String scopeTenantId, Long id) {
        String normalizedRole = normalizeRole(role);
        String normalizedActor = normalizeActor(actor);
        String tenantId = normalizeOptionalTenantId(scopeTenantId);
        enforceRoleScopeCompatibility(normalizedRole, tenantId);
        return blockingDb.run(() -> transactionTemplate.executeWithoutResult(status -> {
            RejectApplication existing = rejectApplicationRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App policy not found"));
            enforceWritable(normalizedRole, tenantId, existing.getTenantId(), "You cannot delete this app policy");
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(normalizedActor);
            rejectApplicationRepository.save(existing);
            recordAudit("REJECT_APPLICATION", "DELETE", normalizedActor, existing.getTenantId(), existing.getId(), existing, null, null);

            for (RuleRemediationMapping mapping : loadRejectMappings(tenantId, id)) {
                if (mapping.isDeleted()) {
                    continue;
                }
                mapping.setDeleted(true);
                mapping.setModifiedAt(OffsetDateTime.now());
                mapping.setModifiedBy(normalizedActor);
                ruleRemediationMappingRepository.save(mapping);
                recordAudit("RULE_REMEDIATION_MAPPING", "DELETE", normalizedActor, mapping.getTenantId(), mapping.getId(), mapping, null, null);
            }
        }));
    }

    public Mono<SimplePolicyStarterPackSummary> installStarterPack(String actor, String role, String scopeTenantId) {
        String normalizedRole = normalizeRole(role);
        String normalizedActor = normalizeActor(actor);
        String tenantId = resolveCreateTenantId(normalizedRole, normalizeOptionalTenantId(scopeTenantId));
        return blockingDb.mono(() -> transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            SimplePolicyStarterPackSummary summary = new SimplePolicyStarterPackSummary();
            summary.setScope(tenantId == null ? "global" : tenantId);
            summary.setCreatedAppRules(0);
            summary.setSkippedAppRules(0);

            Map<String, RemediationRule> remediationsByTitle = new HashMap<>();
            for (RemediationRule remediationRule : remediationRuleRepository.findPaged(tenantId, null, POLICY_FETCH_LIMIT, 0)) {
                if (!isOwnedScope(tenantId, remediationRule.getTenantId())) {
                    continue;
                }
                String key = normalizeLookupKey(remediationRule.getTitle());
                if (key != null) {
                    remediationsByTitle.putIfAbsent(key, remediationRule);
                }
            }
            for (StarterFixSpec fix : STARTER_FIXES) {
                ensureStarterRemediationRule(
                        summary,
                        normalizedActor,
                        tenantId,
                        now,
                        remediationsByTitle,
                        fix.codeStem(),
                        fix.title(),
                        fix.description(),
                        fix.remediationType(),
                        fix.osType(),
                        fix.deviceType(),
                        fix.instructionJson(),
                        fix.priority()
                );
            }

            Map<String, SystemInformationRule> rulesByTag = new HashMap<>();
            for (SystemInformationRule rule : systemRuleRepository.findPaged(tenantId, null, POLICY_FETCH_LIMIT, 0)) {
                if (!isOwnedScope(tenantId, rule.getTenantId())) {
                    continue;
                }
                String key = normalizeLookupKey(rule.getRuleTag());
                if (key != null) {
                    rulesByTag.putIfAbsent(key, rule);
                }
            }

            for (StarterDeviceRuleSpec deviceRule : STARTER_DEVICE_RULES) {
                ensureStarterDeviceRule(
                        summary,
                        normalizedActor,
                        tenantId,
                        now,
                        rulesByTag,
                        deviceRule.name(),
                        deviceRule.description(),
                        deviceRule.osType(),
                        deviceRule.deviceType(),
                        deviceRule.severity(),
                        deviceRule.fieldName(),
                        deviceRule.operator(),
                        deviceRule.valueType(),
                        deviceRule.valueText(),
                        deviceRule.valueNumeric(),
                        deviceRule.valueBoolean(),
                        remediationsByTitle.get(normalizeLookupKey(deviceRule.remediationTitle()))
                );
            }

            Map<String, RejectApplication> appsByKey = new HashMap<>();
            for (RejectApplication app : rejectApplicationRepository.findPaged(tenantId, null, null, POLICY_FETCH_LIMIT, 0)) {
                if (!isOwnedScope(tenantId, app.getTenantId())) {
                    continue;
                }
                String key = starterAppKey(
                        app.getPolicyTag(),
                        app.getAppOsType(),
                        app.getAppName(),
                        app.getPackageId(),
                        app.getMinAllowedVersion()
                );
                if (key != null) {
                    appsByKey.putIfAbsent(key, app);
                }
            }

            for (StarterAppRuleSpec appRule : STARTER_APP_RULES) {
                ensureStarterAppRule(
                        summary,
                        normalizedActor,
                        tenantId,
                        now,
                        appsByKey,
                        appRule.policyTag(),
                        appRule.appOsType(),
                        appRule.appName(),
                        appRule.packageId(),
                        appRule.publisher(),
                        appRule.minAllowedVersion(),
                        appRule.severity(),
                        remediationsByTitle.get(normalizeLookupKey(appRule.remediationTitle()))
                );
            }

            Map<String, TrustScoreDecisionPolicy> decisionsByKey = new HashMap<>();
            for (TrustScoreDecisionPolicy policy : trustScoreDecisionPolicyRepository.findPaged(tenantId, null, POLICY_FETCH_LIMIT, 0)) {
                if (!isOwnedScope(tenantId, policy.getTenantId())) {
                    continue;
                }
                decisionsByKey.putIfAbsent(trustLevelKey(policy.getScoreMin(), policy.getScoreMax(), policy.getDecisionAction()), policy);
            }

            for (StarterTrustLevelSpec trustLevel : STARTER_TRUST_LEVELS) {
                ensureStarterTrustLevel(
                        summary,
                        normalizedActor,
                        tenantId,
                        now,
                        decisionsByKey,
                        trustLevel.label(),
                        trustLevel.scoreMin(),
                        trustLevel.scoreMax(),
                        trustLevel.decisionAction(),
                        trustLevel.remediationRequired(),
                        trustLevel.responseMessage()
                );
            }

            return summary;
        }));
    }

    private SimpleDevicePolicySummary toDeviceSummary(SystemInformationRule rule,
                                                      List<SystemInformationRuleCondition> conditions,
                                                      List<RuleRemediationMapping> mappings) {
        return toDeviceSummary(rule, conditions, mappings, null);
    }

    private SimpleDevicePolicySummary toDeviceSummary(SystemInformationRule rule,
                                                      List<SystemInformationRuleCondition> conditions,
                                                      List<RuleRemediationMapping> mappings,
                                                      RemediationRule remediationRule) {
        SimpleDevicePolicySummary summary = new SimpleDevicePolicySummary();
        summary.setId(rule.getId());
        summary.setRuleCode(rule.getRuleCode());
        summary.setName(firstNonBlank(rule.getRuleTag(), rule.getRuleCode()));
        summary.setDescription(rule.getDescription());
        summary.setOsType(rule.getOsType());
        summary.setOsName(rule.getOsName());
        summary.setDeviceType(rule.getDeviceType());
        summary.setSeverity(rule.getSeverity());
        summary.setScoreDelta(rule.getRiskScoreDelta());
        summary.setStatus(rule.getStatus());

        String complexityReason = null;
        if (conditions.size() != 1) {
            complexityReason = conditions.isEmpty() ? "Missing condition" : "Multiple conditions";
        } else {
            SystemInformationRuleCondition condition = conditions.getFirst();
            summary.setFieldName(condition.getFieldName());
            summary.setOperator(condition.getOperator());
            if (hasJsonCondition(condition)) {
                complexityReason = "List or JSON condition";
            } else if (condition.getValueBoolean() != null) {
                summary.setValueType("BOOLEAN");
                summary.setValueBoolean(condition.getValueBoolean());
            } else if (condition.getValueNumeric() != null) {
                summary.setValueType("NUMBER");
                summary.setValueNumeric(condition.getValueNumeric());
            } else {
                summary.setValueType("TEXT");
                summary.setValueText(condition.getValueText());
            }
        }

        List<RuleRemediationMapping> liveMappings = nonDeletedMappings(mappings);
        if (liveMappings.size() > 1) {
            complexityReason = complexityReason == null ? "Multiple fix mappings" : complexityReason;
        } else if (!liveMappings.isEmpty()) {
            RuleRemediationMapping mapping = liveMappings.getFirst();
            summary.setRemediationRuleId(mapping.getRemediationRuleId());
            summary.setRemediationTitle(resolveRemediationTitle(remediationRule, mapping.getRemediationRuleId()));
        }

        summary.setComplex(complexityReason != null);
        summary.setComplexityReason(complexityReason);
        return summary;
    }

    private SimpleAppPolicySummary toAppSummary(RejectApplication app, List<RuleRemediationMapping> mappings) {
        return toAppSummary(app, mappings, null);
    }

    private SimpleAppPolicySummary toAppSummary(RejectApplication app,
                                                List<RuleRemediationMapping> mappings,
                                                RemediationRule remediationRule) {
        SimpleAppPolicySummary summary = new SimpleAppPolicySummary();
        summary.setId(app.getId());
        summary.setPolicyTag(app.getPolicyTag());
        summary.setSeverity(app.getSeverity());
        summary.setScoreDelta(defaultRejectDelta(app.getSeverity()));
        summary.setAppName(app.getAppName());
        summary.setPublisher(app.getPublisher());
        summary.setPackageId(app.getPackageId());
        summary.setAppOsType(app.getAppOsType());
        summary.setMinAllowedVersion(displayMinAllowedVersion(app.getMinAllowedVersion()));
        summary.setStatus(app.getStatus());

        List<RuleRemediationMapping> liveMappings = nonDeletedMappings(mappings);
        if (liveMappings.size() > 1) {
            summary.setComplex(true);
            summary.setComplexityReason("Multiple fix mappings");
        } else if (!liveMappings.isEmpty()) {
            RuleRemediationMapping mapping = liveMappings.getFirst();
            summary.setRemediationRuleId(mapping.getRemediationRuleId());
            summary.setRemediationTitle(resolveRemediationTitle(remediationRule, mapping.getRemediationRuleId()));
        }
        return summary;
    }

    private SystemInformationRuleCondition buildCondition(SimpleDevicePolicyRequest body,
                                                         SystemInformationRuleCondition existing,
                                                         Long ruleId,
                                                         OffsetDateTime now,
                                                         String actor) {
        String operator = normalizeRequiredChoice(body.getOperator(), CONDITION_OPERATORS, "operator is invalid");
        SystemInformationRuleCondition condition = new SystemInformationRuleCondition();
        if (existing != null) {
            condition.setId(existing.getId());
            condition.setCreatedAt(existing.getCreatedAt());
            condition.setCreatedBy(existing.getCreatedBy());
            condition.setDeleted(existing.isDeleted());
        } else {
            condition.setCreatedAt(now);
            condition.setCreatedBy(actor);
            condition.setDeleted(false);
        }
        condition.setSystemInformationRuleId(ruleId);
        condition.setConditionGroup((short) 1);
        condition.setFieldName(normalizeRequiredText(body.getFieldName(), "field_name is required"));
        condition.setOperator(operator);
        condition.setWeight((short) 1);
        condition.setStatus("ACTIVE");
        condition.setModifiedAt(now);
        condition.setModifiedBy(actor);

        if ("EXISTS".equals(operator) || "NOT_EXISTS".equals(operator)) {
            condition.setValueText(null);
            condition.setValueNumeric(null);
            condition.setValueBoolean(null);
            condition.setValueJson(null);
            return condition;
        }

        String valueType = normalizeRequiredChoice(body.getValueType(), VALUE_TYPES, "value_type is invalid");
        condition.setValueText(null);
        condition.setValueNumeric(null);
        condition.setValueBoolean(null);
        condition.setValueJson(null);
        switch (valueType) {
            case "TEXT" -> condition.setValueText(normalizeRequiredText(body.getValueText(), "value_text is required"));
            case "NUMBER" -> {
                if (body.getValueNumeric() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value_numeric is required");
                }
                condition.setValueNumeric(body.getValueNumeric());
            }
            case "BOOLEAN" -> {
                if (body.getValueBoolean() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value_boolean is required");
                }
                condition.setValueBoolean(body.getValueBoolean());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value_type is invalid");
        }
        return condition;
    }

    private List<RuleRemediationMapping> syncSystemRuleMappings(String actor,
                                                                String tenantId,
                                                                Long ruleId,
                                                                Long remediationRuleId,
                                                                OffsetDateTime now,
                                                                String parentStatus) {
        List<RuleRemediationMapping> existingMappings = loadSystemRuleMappings(tenantId, ruleId);
        for (RuleRemediationMapping mapping : existingMappings) {
            if (mapping.isDeleted()) {
                continue;
            }
            mapping.setDeleted(true);
            mapping.setModifiedAt(now);
            mapping.setModifiedBy(actor);
            ruleRemediationMappingRepository.save(mapping);
            recordAudit("RULE_REMEDIATION_MAPPING", "DELETE", actor, mapping.getTenantId(), mapping.getId(), mapping, null, null);
        }

        if (remediationRuleId == null) {
            return List.of();
        }

        RuleRemediationMapping created = new RuleRemediationMapping();
        created.setSourceType("SYSTEM_RULE");
        created.setSystemInformationRuleId(ruleId);
        created.setRemediationRuleId(remediationRuleId);
        created.setEnforceMode("MANUAL");
        created.setRankOrder((short) 1);
        created.setTenantId(tenantId);
        created.setStatus(normalizeStatus(parentStatus));
        created.setEffectiveFrom(now);
        created.setDeleted(false);
        created.setCreatedAt(now);
        created.setCreatedBy(actor);
        created.setModifiedAt(now);
        created.setModifiedBy(actor);
        RuleRemediationMapping persisted = ruleRemediationMappingRepository.save(created);
        recordAudit("RULE_REMEDIATION_MAPPING", "CREATE", actor, persisted.getTenantId(), persisted.getId(), null, persisted, null);
        return List.of(persisted);
    }

    private List<RuleRemediationMapping> syncRejectMappings(String actor,
                                                            String tenantId,
                                                            Long rejectId,
                                                            Long remediationRuleId,
                                                            OffsetDateTime now,
                                                            String parentStatus) {
        List<RuleRemediationMapping> existingMappings = loadRejectMappings(tenantId, rejectId);
        for (RuleRemediationMapping mapping : existingMappings) {
            if (mapping.isDeleted()) {
                continue;
            }
            mapping.setDeleted(true);
            mapping.setModifiedAt(now);
            mapping.setModifiedBy(actor);
            ruleRemediationMappingRepository.save(mapping);
            recordAudit("RULE_REMEDIATION_MAPPING", "DELETE", actor, mapping.getTenantId(), mapping.getId(), mapping, null, null);
        }

        if (remediationRuleId == null) {
            return List.of();
        }

        RuleRemediationMapping created = new RuleRemediationMapping();
        created.setSourceType("REJECT_APPLICATION");
        created.setRejectApplicationListId(rejectId);
        created.setRemediationRuleId(remediationRuleId);
        created.setEnforceMode("MANUAL");
        created.setRankOrder((short) 1);
        created.setTenantId(tenantId);
        created.setStatus(normalizeStatus(parentStatus));
        created.setEffectiveFrom(now);
        created.setDeleted(false);
        created.setCreatedAt(now);
        created.setCreatedBy(actor);
        created.setModifiedAt(now);
        created.setModifiedBy(actor);
        RuleRemediationMapping persisted = ruleRemediationMappingRepository.save(created);
        recordAudit("RULE_REMEDIATION_MAPPING", "CREATE", actor, persisted.getTenantId(), persisted.getId(), null, persisted, null);
        return List.of(persisted);
    }

    private List<RuleRemediationMapping> loadSystemRuleMappings(String tenantId, Long ruleId) {
        return nonDeletedMappings(ruleRemediationMappingRepository.findPaged(tenantId, "SYSTEM_RULE", POLICY_FETCH_LIMIT, 0))
                .stream()
                .filter(mapping -> Objects.equals(mapping.getSystemInformationRuleId(), ruleId))
                .toList();
    }

    private List<RuleRemediationMapping> loadRejectMappings(String tenantId, Long rejectId) {
        return nonDeletedMappings(ruleRemediationMappingRepository.findPaged(tenantId, "REJECT_APPLICATION", POLICY_FETCH_LIMIT, 0))
                .stream()
                .filter(mapping -> Objects.equals(mapping.getRejectApplicationListId(), rejectId))
                .toList();
    }

    private List<RuleRemediationMapping> nonDeletedMappings(List<RuleRemediationMapping> mappings) {
        return mappings == null ? List.of() : mappings.stream().filter(mapping -> !mapping.isDeleted()).toList();
    }

    private boolean hasJsonCondition(SystemInformationRuleCondition condition) {
        return condition.getValueJson() != null && !condition.getValueJson().isBlank();
    }

    private RemediationRule requireReadableRemediation(String role, String scopeTenantId, Long remediationRuleId) {
        RemediationRule remediationRule = remediationRuleRepository.findById(remediationRuleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "remediation_rule_id not found"));
        enforceReadable(role, scopeTenantId, remediationRule.getTenantId(), "remediation_rule_id not found");
        return remediationRule;
    }

    private RemediationRule ensureStarterRemediationRule(SimplePolicyStarterPackSummary summary,
                                                         String actor,
                                                         String tenantId,
                                                         OffsetDateTime now,
                                                         Map<String, RemediationRule> remediationsByTitle,
                                                         String codeStem,
                                                         String title,
                                                         String description,
                                                         String remediationType,
                                                         String osType,
                                                         String deviceType,
                                                         String instructionJson,
                                                         short priority) {
        String key = normalizeLookupKey(title);
        RemediationRule existing = key == null ? null : remediationsByTitle.get(key);
        if (existing != null) {
            summary.setSkippedFixes(summary.getSkippedFixes() + 1);
            return existing;
        }

        RemediationRule remediationRule = new RemediationRule();
        remediationRule.setRemediationCode(starterRemediationCode(codeStem, tenantId));
        remediationRule.setTitle(title);
        remediationRule.setDescription(description);
        remediationRule.setRemediationType(remediationType);
        remediationRule.setOsType(osType);
        remediationRule.setDeviceType(deviceType);
        remediationRule.setInstructionJson(instructionJson);
        remediationRule.setPriority(priority);
        remediationRule.setTenantId(tenantId);
        remediationRule.setStatus("ACTIVE");
        remediationRule.setEffectiveFrom(now);
        remediationRule.setDeleted(false);
        remediationRule.setCreatedAt(now);
        remediationRule.setCreatedBy(actor);
        remediationRule.setModifiedAt(now);
        remediationRule.setModifiedBy(actor);
        RemediationRule persisted = remediationRuleRepository.save(remediationRule);
        recordAudit("REMEDIATION_RULE", "CREATE", actor, persisted.getTenantId(), persisted.getId(), null, persisted, null);
        if (key != null) {
            remediationsByTitle.put(key, persisted);
        }
        summary.setCreatedFixes(summary.getCreatedFixes() + 1);
        return persisted;
    }

    private void ensureStarterDeviceRule(SimplePolicyStarterPackSummary summary,
                                         String actor,
                                         String tenantId,
                                         OffsetDateTime now,
                                         Map<String, SystemInformationRule> rulesByTag,
                                         String name,
                                         String description,
                                         String osType,
                                         String deviceType,
                                         short severity,
                                         String fieldName,
                                         String operator,
                                         String valueType,
                                         String valueText,
                                         Double valueNumeric,
                                         Boolean valueBoolean,
                                         RemediationRule remediationRule) {
        String key = normalizeLookupKey(name);
        if (key != null && rulesByTag.containsKey(key)) {
            summary.setSkippedDeviceChecks(summary.getSkippedDeviceChecks() + 1);
            return;
        }

        SystemInformationRule rule = new SystemInformationRule();
        rule.setRuleCode(generateSystemRuleCode());
        rule.setRuleTag(name);
        rule.setDescription(description);
        rule.setOsType(osType);
        rule.setDeviceType(deviceType);
        rule.setSeverity(severity);
        rule.setPriority(defaultPriority(severity));
        rule.setVersion(1);
        rule.setMatchMode("ALL");
        rule.setComplianceAction(defaultComplianceAction(severity));
        rule.setRiskScoreDelta(severityToScoreDelta(severity));
        rule.setTenantId(tenantId);
        rule.setStatus("ACTIVE");
        rule.setEffectiveFrom(now);
        rule.setDeleted(false);
        rule.setCreatedAt(now);
        rule.setCreatedBy(actor);
        rule.setModifiedAt(now);
        rule.setModifiedBy(actor);
        SystemInformationRule persistedRule = systemRuleRepository.save(rule);
        recordAudit("SYSTEM_RULE", "CREATE", actor, persistedRule.getTenantId(), persistedRule.getId(), null, persistedRule, null);

        SimpleDevicePolicyRequest request = new SimpleDevicePolicyRequest();
        request.setFieldName(fieldName);
        request.setOperator(operator);
        request.setValueType(valueType);
        request.setValueText(valueText);
        request.setValueNumeric(valueNumeric);
        request.setValueBoolean(valueBoolean);
        SystemInformationRuleCondition condition = buildCondition(request, null, persistedRule.getId(), now, actor);
        SystemInformationRuleCondition persistedCondition = conditionRepository.save(condition);
        recordAudit("SYSTEM_RULE_CONDITION", "CREATE", actor, persistedRule.getTenantId(), persistedCondition.getId(), null, persistedCondition, null);

        syncSystemRuleMappings(
                actor,
                tenantId,
                persistedRule.getId(),
                remediationRule == null ? null : remediationRule.getId(),
                now,
                persistedRule.getStatus()
        );

        if (key != null) {
            rulesByTag.put(key, persistedRule);
        }
        summary.setCreatedDeviceChecks(summary.getCreatedDeviceChecks() + 1);
    }

    private void ensureStarterAppRule(SimplePolicyStarterPackSummary summary,
                                      String actor,
                                      String tenantId,
                                      OffsetDateTime now,
                                      Map<String, RejectApplication> appsByKey,
                                      String policyTag,
                                      String appOsType,
                                      String appName,
                                      String packageId,
                                      String publisher,
                                      String minAllowedVersion,
                                      short severity,
                                      RemediationRule remediationRule) {
        String key = starterAppKey(policyTag, appOsType, appName, packageId, minAllowedVersion);
        if (key != null && appsByKey.containsKey(key)) {
            summary.setSkippedAppRules(summary.getSkippedAppRules() + 1);
            return;
        }

        RejectApplication app = new RejectApplication();
        app.setPolicyTag(policyTag);
        app.setSeverity(severity);
        app.setAppName(appName);
        app.setPublisher(publisher);
        app.setPackageId(packageId);
        app.setAppOsType(appOsType);
        app.setMinAllowedVersion(normalizeAppMinAllowedVersion(minAllowedVersion));
        app.setTenantId(tenantId);
        app.setStatus("ACTIVE");
        app.setEffectiveFrom(now);
        app.setDeleted(false);
        app.setCreatedAt(now);
        app.setCreatedBy(actor);
        app.setModifiedAt(now);
        app.setModifiedBy(actor);
        RejectApplication persisted = rejectApplicationRepository.save(app);
        recordAudit("REJECT_APPLICATION", "CREATE", actor, persisted.getTenantId(), persisted.getId(), null, persisted, null);

        syncRejectMappings(
                actor,
                tenantId,
                persisted.getId(),
                remediationRule == null ? null : remediationRule.getId(),
                now,
                persisted.getStatus()
        );

        if (key != null) {
            appsByKey.put(key, persisted);
        }
        summary.setCreatedAppRules(summary.getCreatedAppRules() + 1);
    }

    private void ensureStarterTrustLevel(SimplePolicyStarterPackSummary summary,
                                         String actor,
                                         String tenantId,
                                         OffsetDateTime now,
                                         Map<String, TrustScoreDecisionPolicy> decisionsByKey,
                                         String label,
                                         short scoreMin,
                                         short scoreMax,
                                         String decisionAction,
                                         boolean remediationRequired,
                                         String responseMessage) {
        String key = trustLevelKey(scoreMin, scoreMax, decisionAction);
        if (decisionsByKey.containsKey(key)) {
            summary.setSkippedTrustLevels(summary.getSkippedTrustLevels() + 1);
            return;
        }

        TrustScoreDecisionPolicy policy = new TrustScoreDecisionPolicy();
        policy.setPolicyName(starterTrustLevelName(tenantId, label));
        policy.setScoreMin(scoreMin);
        policy.setScoreMax(scoreMax);
        policy.setDecisionAction(decisionAction);
        policy.setRemediationRequired(remediationRequired);
        policy.setResponseMessage(responseMessage);
        policy.setTenantId(tenantId);
        policy.setStatus("ACTIVE");
        policy.setEffectiveFrom(now);
        policy.setDeleted(false);
        policy.setCreatedAt(now);
        policy.setCreatedBy(actor);
        policy.setModifiedAt(now);
        policy.setModifiedBy(actor);
        TrustScoreDecisionPolicy persisted = trustScoreDecisionPolicyRepository.save(policy);
        recordAudit("TRUST_DECISION_POLICY", "CREATE", actor, persisted.getTenantId(), persisted.getId(), null, persisted, null);
        decisionsByKey.put(key, persisted);
        summary.setCreatedTrustLevels(summary.getCreatedTrustLevels() + 1);
    }

    private String starterRemediationCode(String codeStem, String tenantId) {
        String normalizedStem = normalizeUpper(codeStem);
        String scopeSuffix = tenantId == null ? "GLOBAL" : sanitizeCodeToken(tenantId);
        return "SIMPLIFIED_" + normalizedStem + "_" + scopeSuffix;
    }

    private String starterTrustLevelName(String tenantId, String label) {
        if (tenantId == null) {
            return "Starter: " + label;
        }
        return "Starter (" + tenantId + "): " + label;
    }

    private String starterAppKey(String policyTag,
                                 String appOsType,
                                 String appName,
                                 String packageId,
                                 String minAllowedVersion) {
        String normalizedPolicyTag = normalizeLookupKey(policyTag);
        String normalizedAppOsType = normalizeUpper(appOsType);
        String normalizedAppName = normalizeLookupKey(appName);
        String normalizedPackageId = normalizeLookupKey(packageId);
        String normalizedMinAllowedVersion = normalizeLookupKey(normalizeAppMinAllowedVersion(minAllowedVersion));
        if (normalizedPolicyTag == null || normalizedAppOsType == null || normalizedAppName == null) {
            return null;
        }
        return normalizedPolicyTag
                + "|"
                + normalizedAppOsType
                + "|"
                + defaultIfBlank(normalizedAppName, "")
                + "|"
                + defaultIfBlank(normalizedPackageId, "")
                + "|"
                + defaultIfBlank(normalizedMinAllowedVersion, "");
    }

    private String sanitizeCodeToken(String value) {
        String normalized = normalizeUpper(defaultIfBlank(value, "GLOBAL"));
        return normalized.replaceAll("[^A-Z0-9]+", "_");
    }

    private String trustLevelKey(Short scoreMin, Short scoreMax, String decisionAction) {
        return safeInt(scoreMin == null ? null : Integer.valueOf(scoreMin), -1)
                + "|"
                + safeInt(scoreMax == null ? null : Integer.valueOf(scoreMax), -1)
                + "|"
                + defaultIfBlank(normalizeUpper(decisionAction), "UNKNOWN");
    }

    private String normalizeLookupKey(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeAppMinAllowedVersion(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? ANY_VERSION_SENTINEL : normalized;
    }

    private String displayMinAllowedVersion(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null || ANY_VERSION_SENTINEL.equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private void validateDeviceRequest(SimpleDevicePolicyRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        normalizeRequiredText(body.getName(), "name is required");
        normalizeRequiredChoice(body.getOsType(), OS_TYPES, "os_type is invalid");
        normalizeRequiredText(body.getFieldName(), "field_name is required");
        normalizeRequiredChoice(body.getOperator(), CONDITION_OPERATORS, "operator is invalid");
        normalizeSeverity(body.getSeverity());
        normalizeStatus(body.getStatus());
    }

    private void validateAppRequest(SimpleAppPolicyRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        normalizeRequiredText(body.getPolicyTag(), "policy_tag is required");
        normalizeRequiredText(body.getAppName(), "app_name is required");
        normalizeRequiredChoice(body.getAppOsType(), OS_TYPES, "app_os_type is invalid");
        normalizeSeverity(body.getSeverity());
        normalizeStatus(body.getStatus());
    }

    private short normalizeSeverity(Short value) {
        short severity = value == null ? (short) 3 : value;
        if (severity < 1 || severity > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "severity must be between 1 and 5");
        }
        return severity;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeUpper(defaultIfBlank(status, "ACTIVE"));
        if (!STATUS_VALUES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is invalid");
        }
        return normalized;
    }

    private String normalizeRequiredChoice(String value, Set<String> allowedValues, String errorMessage) {
        String normalized = normalizeUpper(value);
        if (normalized == null || !allowedValues.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    private String normalizeOptionalChoice(String value, Set<String> allowedValues, String errorMessage) {
        String normalized = normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        if (!allowedValues.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String errorMessage) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private short severityToScoreDelta(short severity) {
        return switch (severity) {
            case 1 -> -10;
            case 2 -> -20;
            case 3 -> -30;
            case 4 -> -45;
            case 5 -> -60;
            default -> -30;
        };
    }

    private short defaultRejectDelta(Short severity) {
        short safeSeverity = severity == null ? (short) 3 : severity;
        return (short) Math.max(-80, -10 * safeSeverity);
    }

    private String defaultComplianceAction(short severity) {
        if (severity >= 5) {
            return "BLOCK";
        }
        if (severity >= 3) {
            return "QUARANTINE";
        }
        return "NOTIFY";
    }

    private int defaultPriority(short severity) {
        return switch (severity) {
            case 5 -> 10;
            case 4 -> 25;
            case 3 -> 50;
            case 2 -> 100;
            default -> 150;
        };
    }

    private int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String generateSystemRuleCode() {
        return "SR-" + OffsetDateTime.now().format(RULE_CODE_TS_FMT) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String resolveRemediationTitle(RemediationRule remediationRule, Long remediationRuleId) {
        if (remediationRule != null && Objects.equals(remediationRule.getId(), remediationRuleId)) {
            return firstNonBlank(remediationRule.getTitle(), remediationRule.getRemediationCode());
        }
        if (remediationRuleId == null) {
            return null;
        }
        return remediationRuleRepository.findById(remediationRuleId)
                .map(rule -> firstNonBlank(rule.getTitle(), rule.getRemediationCode()))
                .orElse(null);
    }

    private String firstNonBlank(String left, String right) {
        String normalizedLeft = normalizeOptionalText(left);
        return normalizedLeft != null ? normalizedLeft : normalizeOptionalText(right);
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

    private String normalizeActor(String actor) {
        String normalized = normalizeOptionalText(actor);
        return normalized == null ? "system" : normalized;
    }

    private String normalizeRole(String role) {
        String normalized = normalizeUpper(role);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported user role");
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

    private void enforceRoleScopeCompatibility(String role, String scopeTenantId) {
        if (TENANT_SCOPED_ROLES.contains(role) && scopeTenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope is required");
        }
    }

    private String resolveCreateTenantId(String role, String scopeTenantId) {
        if (ROLE_PRODUCT_ADMIN.equals(role)) {
            return scopeTenantId;
        }
        if (TENANT_SCOPED_ROLES.contains(role)) {
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
        if (ROLE_PRODUCT_ADMIN.equals(role)) {
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

        if (TENANT_SCOPED_ROLES.contains(role)) {
            if (scopeTenantId == null || !Objects.equals(scopeTenantId, recordTenant)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
            }
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported user role");
    }

    private boolean canRead(String role, String scopeTenantId, String recordTenantId) {
        String recordTenant = normalizeOptionalTenantId(recordTenantId);
        if (ROLE_PRODUCT_ADMIN.equals(role)) {
            if (scopeTenantId == null) {
                return recordTenant == null;
            }
            return recordTenant == null || Objects.equals(scopeTenantId, recordTenant);
        }
        if (TENANT_SCOPED_ROLES.contains(role)) {
            return scopeTenantId != null && (recordTenant == null || Objects.equals(scopeTenantId, recordTenant));
        }
        return false;
    }

    private boolean isOwnedScope(String scopeTenantId, String recordTenantId) {
        String recordTenant = normalizeOptionalTenantId(recordTenantId);
        if (scopeTenantId == null) {
            return recordTenant == null;
        }
        return Objects.equals(scopeTenantId, recordTenant);
    }

    private int scopePriority(String recordTenantId, String scopeTenantId) {
        String recordTenant = normalizeOptionalTenantId(recordTenantId);
        if (scopeTenantId != null && Objects.equals(scopeTenantId, recordTenant)) {
            return 0;
        }
        if (recordTenant == null) {
            return 1;
        }
        return 2;
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
            // Best-effort fallback for queue-free environments.
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
}
