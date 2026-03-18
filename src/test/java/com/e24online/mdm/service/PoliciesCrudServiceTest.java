package com.e24online.mdm.service;

import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.RuleRemediationMapping;
import com.e24online.mdm.domain.PolicyChangeAudit;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.service.messaging.PolicyAuditPublisher;
import com.e24online.mdm.web.dto.PolicyAuditMessage;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import com.e24online.mdm.repository.TrustScorePolicyRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoliciesCrudServiceTest {

    @Mock
    private SystemInformationRuleRepository systemRuleRepository;
    @Mock
    private SystemInformationRuleConditionRepository conditionRepository;
    @Mock
    private RejectApplicationRepository rejectApplicationRepository;
    @Mock
    private TrustScorePolicyRepository trustScorePolicyRepository;
    @Mock
    private TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    @Mock
    private RemediationRuleRepository remediationRuleRepository;
    @Mock
    private RuleRemediationMappingRepository ruleRemediationMappingRepository;
    @Mock
    private com.e24online.mdm.repository.PolicyChangeAuditRepository policyChangeAuditRepository;
    @Mock
    private PolicyAuditPublisher policyAuditPublisher;
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PoliciesCrudService service;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        service = new PoliciesCrudService(
                systemRuleRepository,
                conditionRepository,
                rejectApplicationRepository,
                trustScorePolicyRepository,
                trustScoreDecisionPolicyRepository,
                remediationRuleRepository,
                ruleRemediationMappingRepository,
                policyChangeAuditRepository,
                policyAuditPublisher,
                auditEventService,
                new BlockingDb(Schedulers.immediate()),
                transactionTemplate,
                objectMapper,
                meterRegistry
        );

        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(conditionRepository.save(any(SystemInformationRuleCondition.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(rejectApplicationRepository.save(any(RejectApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(trustScorePolicyRepository.save(any(TrustScorePolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(trustScoreDecisionPolicyRepository.save(any(TrustScoreDecisionPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(remediationRuleRepository.save(any(RemediationRule.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ruleRemediationMappingRepository.save(any(RuleRemediationMapping.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(policyChangeAuditRepository.save(any(PolicyChangeAudit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listApis_normalizePagingAcrossAllPolicyTypes() {
        when(systemRuleRepository.findPaged(null, "ACTIVE", 50, 0L)).thenReturn(List.of(systemRule(1L)));
        when(rejectApplicationRepository.findPaged(null, "ANDROID", "ACTIVE", 500, 500L)).thenReturn(List.of(rejectApp(2L)));
        when(trustScorePolicyRepository.findPaged(null, "ACTIVE", 500, 0L)).thenReturn(List.of(trustPolicy(3L)));
        when(trustScoreDecisionPolicyRepository.findPaged(null, "ACTIVE", 500, 0L)).thenReturn(List.of(decisionPolicy(4L)));
        when(remediationRuleRepository.findPaged(null, "ACTIVE", 50, 100L)).thenReturn(List.of(remediationRule(5L)));
        when(ruleRemediationMappingRepository.findPaged(null, "SYSTEM_RULE", 500, 0L)).thenReturn(List.of(mapping(6L)));

        assertEquals(1, service.listSystemRules("ACTIVE", -10, 0).collectList().block().size());
        assertEquals(1, service.listRejectApps("ANDROID", "ACTIVE", 1, 10000).collectList().block().size());
        assertEquals(1, service.listTrustScorePolicies("ACTIVE", -1, 10000).collectList().block().size());
        assertEquals(1, service.listTrustDecisionPolicies("ACTIVE", -1, 10000).collectList().block().size());
        assertEquals(1, service.listRemediationRules("ACTIVE", 2, -100).collectList().block().size());
        assertEquals(1, service.listRuleRemediationMappings("SYSTEM_RULE", 0, 999).collectList().block().size());
    }

    @Test
    void getApis_returnEntitiesOrNotFound() {
        when(systemRuleRepository.findById(10L)).thenReturn(Optional.of(systemRule(10L)));
        when(conditionRepository.findByRuleId(10L)).thenReturn(List.of(condition(11L, 10L)));
        when(rejectApplicationRepository.findById(12L)).thenReturn(Optional.of(rejectApp(12L)));
        when(trustScorePolicyRepository.findById(13L)).thenReturn(Optional.of(trustPolicy(13L)));
        when(trustScoreDecisionPolicyRepository.findById(14L)).thenReturn(Optional.of(decisionPolicy(14L)));
        when(remediationRuleRepository.findById(15L)).thenReturn(Optional.of(remediationRule(15L)));
        when(ruleRemediationMappingRepository.findById(16L)).thenReturn(Optional.of(mapping(16L)));
        when(systemRuleRepository.findById(404L)).thenReturn(Optional.empty());

        assertEquals(10L, service.getSystemRule(10L).block().getId());
        assertEquals(1, service.listSystemRuleConditions(10L).collectList().block().size());
        assertEquals(12L, service.getRejectApp(12L).block().getId());
        assertEquals(13L, service.getTrustScorePolicy(13L).block().getId());
        assertEquals(14L, service.getTrustDecisionPolicy(14L).block().getId());
        assertEquals(15L, service.getRemediationRule(15L).block().getId());
        assertEquals(16L, service.getRuleRemediationMapping(16L).block().getId());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getSystemRule(404L).block());
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void createAndUpdateSystemRule_applyDefaultsAndPreserveCreatedFields() {
        SystemInformationRule createBody = new SystemInformationRule();
        createBody.setRuleCode("R-CREATE");
        SystemInformationRule created = service.createSystemRule(null, reactor.core.publisher.Mono.just(createBody)).block();
        assertNotNull(created.getCreatedAt());
        assertEquals("ui", created.getCreatedBy());
        assertEquals("ACTIVE", created.getStatus());
        assertNull(created.getId());
        assertEquals(false, created.isDeleted());

        SystemInformationRule existing = systemRule(33L);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(2));
        existing.setCreatedBy("seed");
        existing.setStatus("INACTIVE");
        existing.setDeleted(true);
        existing.setEffectiveFrom(OffsetDateTime.now().minusDays(3));
        when(systemRuleRepository.findById(33L)).thenReturn(Optional.of(existing));

        SystemInformationRule updateBody = new SystemInformationRule();
        updateBody.setRuleCode("R-UPDATED");
        SystemInformationRule updated = service.updateSystemRule("actor-x", 33L, reactor.core.publisher.Mono.just(updateBody)).block();

        assertEquals(33L, updated.getId());
        assertEquals(existing.getRuleCode(), updated.getRuleCode());
        assertEquals("seed", updated.getCreatedBy());
        assertEquals(existing.getCreatedAt(), updated.getCreatedAt());
        assertEquals(true, updated.isDeleted());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals("actor-x", updated.getModifiedBy());
    }

    @Test
    void createSystemRule_generatesRuleCodeWhenMissing() {
        SystemInformationRule createBody = new SystemInformationRule();
        SystemInformationRule created = service.createSystemRule(null, reactor.core.publisher.Mono.just(createBody)).block();
        assertNotNull(created.getRuleCode());
        assertTrue(created.getRuleCode().startsWith("SR-"));
    }

    @Test
    void deleteSystemRule_marksDeleted() {
        SystemInformationRule existing = systemRule(44L);
        when(systemRuleRepository.findById(44L)).thenReturn(Optional.of(existing));

        service.deleteSystemRule("deleter", 44L).block();

        assertEquals(true, existing.isDeleted());
        assertEquals("deleter", existing.getModifiedBy());
    }

    @Test
    void createUpdateDeleteSystemRuleCondition_coversConflictBranch() {
        SystemInformationRuleCondition createBody = new SystemInformationRuleCondition();
        createBody.setFieldName("os_type");
        createBody.setOperator("EQ");
        when(systemRuleRepository.findById(70L)).thenReturn(Optional.of(systemRule(70L)));
        SystemInformationRuleCondition created = service.createSystemRuleCondition(
                null,
                70L,
                reactor.core.publisher.Mono.just(createBody)
        ).block();
        assertEquals(70L, created.getSystemInformationRuleId());
        assertEquals((short) 1, created.getConditionGroup());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("ui", created.getCreatedBy());

        SystemInformationRuleCondition existing = condition(71L, 70L);
        existing.setConditionGroup((short) 3);
        existing.setStatus("INACTIVE");
        existing.setCreatedBy("seed");
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        when(conditionRepository.findById(71L)).thenReturn(Optional.of(existing));

        SystemInformationRuleCondition updateBody = new SystemInformationRuleCondition();
        updateBody.setFieldName("api_level");
        updateBody.setOperator("GTE");
        SystemInformationRuleCondition updated = service.updateSystemRuleCondition(
                "editor",
                70L,
                71L,
                reactor.core.publisher.Mono.just(updateBody)
        ).block();
        assertEquals((short) 3, updated.getConditionGroup());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals("editor", updated.getModifiedBy());

        service.deleteSystemRuleCondition("deleter", 70L, 71L).block();
        assertEquals(true, existing.isDeleted());
        assertEquals("deleter", existing.getModifiedBy());

        SystemInformationRuleCondition wrongRule = condition(72L, 999L);
        when(conditionRepository.findById(72L)).thenReturn(Optional.of(wrongRule));
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteSystemRuleCondition("deleter", 70L, 72L).block()
        );
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void createUpdateDeleteRejectApp_applyDefaults() {
        RejectApplication createBody = new RejectApplication();
        createBody.setAppName("Bad App");
        RejectApplication created = service.createRejectApp("admin", reactor.core.publisher.Mono.just(createBody)).block();
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("admin", created.getCreatedBy());
        assertEquals(false, created.isDeleted());

        RejectApplication existing = rejectApp(90L);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(2));
        existing.setCreatedBy("seed");
        existing.setStatus("INACTIVE");
        existing.setDeleted(true);
        when(rejectApplicationRepository.findById(90L)).thenReturn(Optional.of(existing));

        RejectApplication updateBody = new RejectApplication();
        updateBody.setAppName("Worse App");
        RejectApplication updated = service.updateRejectApp("", 90L, reactor.core.publisher.Mono.just(updateBody)).block();
        assertEquals("ui", updated.getModifiedBy());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals(true, updated.isDeleted());

        service.deleteRejectApp(null, 90L).block();
        assertEquals(true, existing.isDeleted());
        assertEquals("ui", existing.getModifiedBy());
    }

    @Test
    void createUpdateDeleteTrustScorePolicy_appliesWeightDefault() {
        TrustScorePolicy createBody = new TrustScorePolicy();
        createBody.setPolicyCode("P-1");
        createBody.setSourceType("SYSTEM_RULE");
        createBody.setSignalKey("RULE_1");
        createBody.setScoreDelta((short) -10);
        TrustScorePolicy created = service.createTrustScorePolicy("policy-admin", reactor.core.publisher.Mono.just(createBody)).block();
        assertEquals(1.0, created.getWeight());
        assertEquals("ACTIVE", created.getStatus());

        TrustScorePolicy existing = trustPolicy(100L);
        existing.setWeight(2.5);
        existing.setCreatedBy("seed");
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        existing.setStatus("INACTIVE");
        existing.setSourceType("SYSTEM_RULE");
        existing.setSignalKey("RULE_1");
        existing.setScoreDelta((short) -12);
        when(trustScorePolicyRepository.findById(100L)).thenReturn(Optional.of(existing));

        TrustScorePolicy updateBody = new TrustScorePolicy();
        updateBody.setSourceType("SYSTEM_RULE");
        updateBody.setSignalKey("RULE_1");
        updateBody.setScoreDelta((short) -12);
        TrustScorePolicy updated = service.updateTrustScorePolicy(
                "editor",
                100L,
                reactor.core.publisher.Mono.just(updateBody)
        ).block();
        assertEquals(2.5, updated.getWeight());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals("editor", updated.getModifiedBy());

        service.deleteTrustScorePolicy("", 100L).block();
        assertEquals(true, existing.isDeleted());
        assertEquals("ui", existing.getModifiedBy());
    }

    @Test
    void createUpdateDeleteTrustDecisionPolicy() {
        TrustScoreDecisionPolicy createBody = new TrustScoreDecisionPolicy();
        createBody.setPolicyName("decision");
        createBody.setScoreMin((short) 0);
        createBody.setScoreMax((short) 49);
        createBody.setDecisionAction("NOTIFY");
        TrustScoreDecisionPolicy created = service.createTrustDecisionPolicy(null, reactor.core.publisher.Mono.just(createBody)).block();
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("ui", created.getCreatedBy());

        TrustScoreDecisionPolicy existing = decisionPolicy(110L);
        existing.setCreatedBy("seed");
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        existing.setStatus("INACTIVE");
        existing.setScoreMin((short) 50);
        existing.setScoreMax((short) 100);
        existing.setDecisionAction("ALLOW");
        when(trustScoreDecisionPolicyRepository.findById(110L)).thenReturn(Optional.of(existing));

        TrustScoreDecisionPolicy updateBody = new TrustScoreDecisionPolicy();
        updateBody.setScoreMin((short) 50);
        updateBody.setScoreMax((short) 100);
        updateBody.setDecisionAction("ALLOW");
        TrustScoreDecisionPolicy updated = service.updateTrustDecisionPolicy(
                "editor",
                110L,
                reactor.core.publisher.Mono.just(updateBody)
        ).block();
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals("editor", updated.getModifiedBy());

        service.deleteTrustDecisionPolicy("deleter", 110L).block();
        assertEquals(true, existing.isDeleted());
        assertEquals("deleter", existing.getModifiedBy());
    }

    @Test
    void createUpdateDeleteRemediationRule_appliesPriorityDefault() {
        RemediationRule createBody = new RemediationRule();
        createBody.setRemediationCode("REM-1");
        RemediationRule created = service.createRemediationRule(null, reactor.core.publisher.Mono.just(createBody)).block();
        assertEquals((short) 100, created.getPriority());
        assertEquals("ACTIVE", created.getStatus());

        RemediationRule existing = remediationRule(120L);
        existing.setPriority((short) 9);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        existing.setCreatedBy("seed");
        existing.setStatus("INACTIVE");
        when(remediationRuleRepository.findById(120L)).thenReturn(Optional.of(existing));

        RemediationRule updated = service.updateRemediationRule(
                "editor",
                120L,
                reactor.core.publisher.Mono.just(new RemediationRule())
        ).block();
        assertEquals(existing.getRemediationCode(), updated.getRemediationCode());
        assertEquals((short) 9, updated.getPriority());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals("editor", updated.getModifiedBy());

        service.deleteRemediationRule("deleter", 120L).block();
        assertEquals(true, existing.isDeleted());
    }

    @Test
    void createRemediationRule_generatesRemediationCodeWhenMissing() {
        RemediationRule createBody = new RemediationRule();
        createBody.setRemediationType("OS_UPDATE");
        RemediationRule created = service.createRemediationRule(null, reactor.core.publisher.Mono.just(createBody)).block();
        assertNotNull(created.getRemediationCode());
        assertTrue(created.getRemediationCode().startsWith("RM-"));
        assertTrue(created.getRemediationCode().contains("-OS_UPDATE-"));
    }

    @Test
    void createUpdateDeleteRuleRemediationMapping_appliesDefaults() {
        RuleRemediationMapping createBody = new RuleRemediationMapping();
        createBody.setSourceType("SYSTEM_RULE");
        createBody.setSystemInformationRuleId(123L);
        createBody.setRemediationRuleId(333L);
        when(systemRuleRepository.findById(123L)).thenReturn(Optional.of(systemRule(123L)));
        when(remediationRuleRepository.findById(333L)).thenReturn(Optional.of(remediationRule(333L)));
        RuleRemediationMapping created = service.createRuleRemediationMapping(
                "mapper",
                reactor.core.publisher.Mono.just(createBody)
        ).block();
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("ADVISORY", created.getEnforceMode());
        assertEquals((short) 1, created.getRankOrder());

        RuleRemediationMapping existing = mapping(130L);
        existing.setEnforceMode("ENFORCE");
        existing.setRankOrder((short) 7);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        existing.setCreatedBy("seed");
        existing.setStatus("INACTIVE");
        when(ruleRemediationMappingRepository.findById(130L)).thenReturn(Optional.of(existing));
        when(remediationRuleRepository.findById(existing.getRemediationRuleId())).thenReturn(Optional.of(remediationRule(existing.getRemediationRuleId())));
        when(systemRuleRepository.findById(existing.getSystemInformationRuleId())).thenReturn(Optional.of(systemRule(existing.getSystemInformationRuleId())));

        RuleRemediationMapping updateBody = new RuleRemediationMapping();
        updateBody.setSourceType("SYSTEM_RULE");
        updateBody.setSystemInformationRuleId(existing.getSystemInformationRuleId());
        updateBody.setRemediationRuleId(existing.getRemediationRuleId());
        RuleRemediationMapping updated = service.updateRuleRemediationMapping(
                "",
                130L,
                reactor.core.publisher.Mono.just(updateBody)
        ).block();
        assertEquals("ENFORCE", updated.getEnforceMode());
        assertEquals((short) 7, updated.getRankOrder());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals("ui", updated.getModifiedBy());

        service.deleteRuleRemediationMapping(null, 130L).block();
        assertEquals(true, existing.isDeleted());
    }

    @Test
    void notFoundPaths_forUpdateAndDeleteMethodsThrow404() {
        when(remediationRuleRepository.findById(999L)).thenReturn(Optional.empty());
        when(ruleRemediationMappingRepository.findById(998L)).thenReturn(Optional.empty());

        ResponseStatusException updateEx = assertThrows(
                ResponseStatusException.class,
                () -> service.updateRemediationRule("x", 999L, reactor.core.publisher.Mono.just(new RemediationRule())).block()
        );
        assertEquals(404, updateEx.getStatusCode().value());

        ResponseStatusException deleteEx = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteRuleRemediationMapping("x", 998L).block()
        );
        assertEquals(404, deleteEx.getStatusCode().value());
        verify(systemRuleRepository, never()).save(any(SystemInformationRule.class));
    }

    @Test
    void updateGlobalSystemRule_forTenantAdmin_isForbidden() {
        SystemInformationRule global = systemRule(501L);
        global.setTenantId(null);
        when(systemRuleRepository.findById(501L)).thenReturn(Optional.of(global));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateSystemRule(
                        "tenant-admin",
                        "TENANT_ADMIN",
                        "tenant-a",
                        501L,
                        reactor.core.publisher.Mono.just(systemRule(501L))
                ).block()
        );
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void productAdminWithoutTenantScope_cannotDeleteTenantPolicy() {
        SystemInformationRule tenantRule = systemRule(502L);
        tenantRule.setTenantId("tenant-a");
        when(systemRuleRepository.findById(502L)).thenReturn(Optional.of(tenantRule));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteSystemRule("product-admin", "PRODUCT_ADMIN", null, 502L).block()
        );
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void mappingWithMultipleSourceReferences_isRejectedBeforeSave() {
        RuleRemediationMapping createBody = new RuleRemediationMapping();
        createBody.setSourceType("SYSTEM_RULE");
        createBody.setSystemInformationRuleId(10L);
        createBody.setRejectApplicationListId(20L);
        createBody.setRemediationRuleId(30L);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createRuleRemediationMapping("actor", "TENANT_ADMIN", "tenant-a", reactor.core.publisher.Mono.just(createBody)).block()
        );
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void overlappingDecisionRangeInSameWindow_isRejected() {
        TrustScoreDecisionPolicy existing = decisionPolicy(601L);
        existing.setTenantId("tenant-a");
        existing.setStatus("ACTIVE");
        existing.setScoreMin((short) 40);
        existing.setScoreMax((short) 80);
        existing.setEffectiveFrom(OffsetDateTime.now().minusDays(5));
        existing.setEffectiveTo(OffsetDateTime.now().plusDays(5));
        when(trustScoreDecisionPolicyRepository.findAll()).thenReturn(List.of(existing));

        TrustScoreDecisionPolicy createBody = new TrustScoreDecisionPolicy();
        createBody.setPolicyName("overlap");
        createBody.setScoreMin((short) 60);
        createBody.setScoreMax((short) 90);
        createBody.setDecisionAction("BLOCK");
        createBody.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        createBody.setEffectiveTo(OffsetDateTime.now().plusDays(1));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createTrustDecisionPolicy("actor", "TENANT_ADMIN", "tenant-a", reactor.core.publisher.Mono.just(createBody)).block()
        );
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void cloneSystemRule_copiesConditionsTransactionally() {
        SystemInformationRule source = systemRule(701L);
        source.setTenantId(null);
        source.setRuleCode("SR-SOURCE");
        source.setPriority(10);
        source.setVersion(1);
        source.setSeverity((short) 3);
        source.setComplianceAction("ALLOW");
        source.setRiskScoreDelta((short) 0);
        source.setOsType("WINDOWS");
        source.setStatus("ACTIVE");
        source.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        when(systemRuleRepository.findById(701L)).thenReturn(Optional.of(source));
        when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(inv -> {
            SystemInformationRule saved = inv.getArgument(0);
            saved.setId(702L);
            return saved;
        });
        when(conditionRepository.findByRuleId(701L)).thenReturn(List.of(condition(703L, 701L)));

        PoliciesCrudService.SystemRuleCloneResult result = service.cloneSystemRule(
                "actor",
                "TENANT_ADMIN",
                "tenant-a",
                701L
        ).block();

        assertNotNull(result);
        assertNotNull(result.rule());
        assertEquals(702L, result.rule().getId());
        assertEquals("tenant-a", result.rule().getTenantId());
        assertEquals(1, result.clonedConditions());
        verify(conditionRepository).save(any(SystemInformationRuleCondition.class));
    }

    @Test
    void createSystemRule_recordsAuditEntry() {
        when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(inv -> {
            SystemInformationRule saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(811L);
            }
            return saved;
        });

        SystemInformationRule createBody = new SystemInformationRule();
        createBody.setRuleCode("SR-AUDIT-CREATE");
        service.createSystemRule("creator", "TENANT_ADMIN", "tenant-a", reactor.core.publisher.Mono.just(createBody)).block();

        ArgumentCaptor<PolicyAuditMessage> captor = ArgumentCaptor.forClass(PolicyAuditMessage.class);
        verify(policyAuditPublisher).publish(captor.capture());
        PolicyAuditMessage audit = captor.getValue();

        assertEquals("SYSTEM_RULE", audit.getPolicyType());
        assertEquals("CREATE", audit.getOperation());
        assertEquals("creator", audit.getActor());
        assertEquals("tenant-a", audit.getTenantId());
        assertEquals(811L, audit.getPolicyId());
        assertNull(audit.getBeforeStateJson());
        assertNotNull(audit.getAfterStateJson());
    }

    @Test
    void updateSystemRule_recordsAuditEntryWithBeforeAfterSnapshots() {
        SystemInformationRule existing = systemRule(812L);
        existing.setTenantId("tenant-a");
        existing.setRuleCode("SR-BEFORE");
        when(systemRuleRepository.findById(812L)).thenReturn(Optional.of(existing));

        SystemInformationRule updateBody = new SystemInformationRule();
        updateBody.setRuleCode("SR-AFTER");
        service.updateSystemRule("editor", "TENANT_ADMIN", "tenant-a", 812L, reactor.core.publisher.Mono.just(updateBody)).block();

        ArgumentCaptor<PolicyAuditMessage> captor = ArgumentCaptor.forClass(PolicyAuditMessage.class);
        verify(policyAuditPublisher).publish(captor.capture());
        PolicyAuditMessage audit = captor.getValue();

        assertEquals("SYSTEM_RULE", audit.getPolicyType());
        assertEquals("UPDATE", audit.getOperation());
        assertEquals("editor", audit.getActor());
        assertEquals("tenant-a", audit.getTenantId());
        assertEquals(812L, audit.getPolicyId());
        assertNotNull(audit.getBeforeStateJson());
        assertNotNull(audit.getAfterStateJson());
        assertTrue(audit.getBeforeStateJson().contains("\"ruleCode\":\"SR-BEFORE\""));
    }

    @Test
    void deleteSystemRule_recordsDeleteAuditEntry() {
        SystemInformationRule existing = systemRule(813L);
        existing.setTenantId("tenant-a");
        when(systemRuleRepository.findById(813L)).thenReturn(Optional.of(existing));

        service.deleteSystemRule("deleter", "TENANT_ADMIN", "tenant-a", 813L).block();

        ArgumentCaptor<PolicyAuditMessage> captor = ArgumentCaptor.forClass(PolicyAuditMessage.class);
        verify(policyAuditPublisher).publish(captor.capture());
        PolicyAuditMessage audit = captor.getValue();

        assertEquals("SYSTEM_RULE", audit.getPolicyType());
        assertEquals("DELETE", audit.getOperation());
        assertEquals("deleter", audit.getActor());
        assertEquals("tenant-a", audit.getTenantId());
        assertEquals(813L, audit.getPolicyId());
        assertNotNull(audit.getBeforeStateJson());
        assertNull(audit.getAfterStateJson());
    }

    @Test
    void cloneSystemRule_recordsCloneAuditEntry() {
        SystemInformationRule source = systemRule(814L);
        source.setTenantId(null);
        source.setRuleCode("SR-CLONE-SRC");
        when(systemRuleRepository.findById(814L)).thenReturn(Optional.of(source));
        when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(inv -> {
            SystemInformationRule saved = inv.getArgument(0);
            saved.setId(815L);
            return saved;
        });
        when(conditionRepository.findByRuleId(814L)).thenReturn(List.of(condition(820L, 814L)));

        service.cloneSystemRule("cloner", "TENANT_ADMIN", "tenant-a", 814L).block();

        ArgumentCaptor<PolicyAuditMessage> captor = ArgumentCaptor.forClass(PolicyAuditMessage.class);
        verify(policyAuditPublisher).publish(captor.capture());
        PolicyAuditMessage audit = captor.getValue();

        assertEquals("SYSTEM_RULE", audit.getPolicyType());
        assertEquals("CLONE", audit.getOperation());
        assertEquals("cloner", audit.getActor());
        assertEquals("tenant-a", audit.getTenantId());
        assertEquals(815L, audit.getPolicyId());
        assertNotNull(audit.getBeforeStateJson());
        assertNotNull(audit.getAfterStateJson());
        assertTrue(audit.getBeforeStateJson().contains("\"id\":814"));
        assertTrue(audit.getAfterStateJson().contains("\"id\":815"));
    }

    @Test
    void auditCounter_isPublishedPerOperationOutcome() {
        when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(inv -> {
            SystemInformationRule saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(900L);
            }
            return saved;
        });

        SystemInformationRule createBody = new SystemInformationRule();
        createBody.setRuleCode("SR-METRIC");
        service.createSystemRule("metric-user", "TENANT_ADMIN", "tenant-a", reactor.core.publisher.Mono.just(createBody)).block();

        double successCount = meterRegistry
                .find("mdm.policy.audit.events")
                .tags("policy_type", "system_rule", "operation", "create", "scope", "tenant", "outcome", "success")
                .counter()
                .count();
        assertEquals(1.0d, successCount);
        verify(policyAuditPublisher, times(1)).publish(any(PolicyAuditMessage.class));
        verify(policyChangeAuditRepository, never()).save(any(PolicyChangeAudit.class));
    }

    @Test
    void auditPublisherFailure_fallsBackToDirectAuditSave() {
        when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(inv -> {
            SystemInformationRule saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(901L);
            }
            return saved;
        });

        org.mockito.Mockito.doThrow(new RuntimeException("queue down"))
                .when(policyAuditPublisher)
                .publish(any(PolicyAuditMessage.class));

        SystemInformationRule createBody = new SystemInformationRule();
        createBody.setRuleCode("SR-FALLBACK");
        service.createSystemRule("metric-user", "TENANT_ADMIN", "tenant-a", reactor.core.publisher.Mono.just(createBody)).block();

        verify(policyAuditPublisher, times(1)).publish(any(PolicyAuditMessage.class));
        verify(policyChangeAuditRepository, times(1)).save(any(PolicyChangeAudit.class));
    }

    private SystemInformationRule systemRule(Long id) {
        SystemInformationRule rule = new SystemInformationRule();
        rule.setId(id);
        rule.setRuleCode("R-" + id);
        rule.setStatus("ACTIVE");
        rule.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return rule;
    }

    private SystemInformationRuleCondition condition(Long id, Long ruleId) {
        SystemInformationRuleCondition condition = new SystemInformationRuleCondition();
        condition.setId(id);
        condition.setSystemInformationRuleId(ruleId);
        condition.setFieldName("os_type");
        condition.setOperator("EQ");
        condition.setStatus("ACTIVE");
        condition.setConditionGroup((short) 1);
        return condition;
    }

    private RejectApplication rejectApp(Long id) {
        RejectApplication app = new RejectApplication();
        app.setId(id);
        app.setAppName("Bad-" + id);
        app.setAppOsType("ANDROID");
        app.setStatus("ACTIVE");
        app.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return app;
    }

    private TrustScorePolicy trustPolicy(Long id) {
        TrustScorePolicy policy = new TrustScorePolicy();
        policy.setId(id);
        policy.setPolicyCode("P-" + id);
        policy.setSourceType("SYSTEM_RULE");
        policy.setSignalKey("RULE-" + id);
        policy.setScoreDelta((short) -10);
        policy.setStatus("ACTIVE");
        policy.setWeight(1.0);
        policy.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return policy;
    }

    private TrustScoreDecisionPolicy decisionPolicy(Long id) {
        TrustScoreDecisionPolicy policy = new TrustScoreDecisionPolicy();
        policy.setId(id);
        policy.setPolicyName("D-" + id);
        policy.setStatus("ACTIVE");
        policy.setScoreMin((short) 0);
        policy.setScoreMax((short) 100);
        policy.setDecisionAction("ALLOW");
        policy.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return policy;
    }

    private RemediationRule remediationRule(Long id) {
        RemediationRule rule = new RemediationRule();
        rule.setId(id);
        rule.setRemediationCode("R-" + id);
        rule.setStatus("ACTIVE");
        rule.setPriority((short) 10);
        rule.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return rule;
    }

    private RuleRemediationMapping mapping(Long id) {
        RuleRemediationMapping mapping = new RuleRemediationMapping();
        mapping.setId(id);
        mapping.setSourceType("SYSTEM_RULE");
        mapping.setSystemInformationRuleId(10L);
        mapping.setRemediationRuleId(10L);
        mapping.setStatus("ACTIVE");
        mapping.setEnforceMode("ADVISORY");
        mapping.setRankOrder((short) 1);
        mapping.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return mapping;
    }
}
