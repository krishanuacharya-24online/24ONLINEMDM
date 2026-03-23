package com.e24online.mdm.service;

import com.e24online.mdm.domain.PolicyChangeAudit;
import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.RuleRemediationMapping;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.PolicyTemplateApplyReport;
import com.e24online.mdm.repository.PolicyChangeAuditRepository;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import com.e24online.mdm.repository.TrustScorePolicyRepository;
import com.e24online.mdm.service.messaging.PolicyAuditPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyTemplateMaintenanceServiceTest {

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
    private PolicyChangeAuditRepository policyChangeAuditRepository;
    @Mock
    private PolicyAuditPublisher policyAuditPublisher;
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PolicyTemplateMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new PolicyTemplateMaintenanceService(
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
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );

        AtomicLong ids = new AtomicLong(1000L);
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(invocation -> assignRuleId(invocation.getArgument(0), ids));
        lenient().when(conditionRepository.save(any(SystemInformationRuleCondition.class))).thenAnswer(invocation -> assignConditionId(invocation.getArgument(0), ids));
        lenient().when(rejectApplicationRepository.save(any(RejectApplication.class))).thenAnswer(invocation -> assignRejectId(invocation.getArgument(0), ids));
        lenient().when(trustScorePolicyRepository.save(any(TrustScorePolicy.class))).thenAnswer(invocation -> assignTrustPolicyId(invocation.getArgument(0), ids));
        lenient().when(trustScoreDecisionPolicyRepository.save(any(TrustScoreDecisionPolicy.class))).thenAnswer(invocation -> assignDecisionId(invocation.getArgument(0), ids));
        lenient().when(remediationRuleRepository.save(any(RemediationRule.class))).thenAnswer(invocation -> assignRemediationId(invocation.getArgument(0), ids));
        lenient().when(ruleRemediationMappingRepository.save(any(RuleRemediationMapping.class))).thenAnswer(invocation -> assignMappingId(invocation.getArgument(0), ids));
        lenient().when(policyChangeAuditRepository.save(any(PolicyChangeAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void applyProductionPack_retiresExistingPoliciesAcrossScopesAndSeedsBaseline() {
        SystemInformationRule globalRule = rule(1L, "TEST_GLOBAL_RULE", null);
        SystemInformationRule tenantRule = rule(2L, "TEST_TENANT_RULE", "tenant-a");
        SystemInformationRuleCondition globalCondition = condition(11L, 1L);
        SystemInformationRuleCondition tenantCondition = condition(12L, 2L);
        RejectApplication globalReject = rejectApp(21L, "Bad Global App", null);
        RejectApplication tenantReject = rejectApp(22L, "Bad Tenant App", "tenant-a");
        TrustScorePolicy globalTrust = trustPolicy(31L, "TEST_GLOBAL_POLICY", null);
        TrustScorePolicy tenantTrust = trustPolicy(32L, "TEST_TENANT_POLICY", "tenant-a");
        TrustScoreDecisionPolicy globalDecision = decisionPolicy(41L, "Global decision", null);
        TrustScoreDecisionPolicy tenantDecision = decisionPolicy(42L, "Tenant decision", "tenant-a");
        RemediationRule globalFix = remediation(51L, "TEST_GLOBAL_FIX", null);
        RemediationRule tenantFix = remediation(52L, "TEST_TENANT_FIX", "tenant-a");
        RuleRemediationMapping globalMapping = mapping(61L, 1L, 51L, null);
        RuleRemediationMapping tenantMapping = mapping(62L, 2L, 52L, "tenant-a");

        when(systemRuleRepository.findAll()).thenReturn(List.of(globalRule, tenantRule));
        when(conditionRepository.findAll()).thenReturn(List.of(globalCondition, tenantCondition));
        when(rejectApplicationRepository.findAll()).thenReturn(List.of(globalReject, tenantReject));
        when(trustScorePolicyRepository.findAll()).thenReturn(List.of(globalTrust, tenantTrust));
        when(trustScoreDecisionPolicyRepository.findAll()).thenReturn(List.of(globalDecision, tenantDecision));
        when(remediationRuleRepository.findAll()).thenReturn(List.of(globalFix, tenantFix));
        when(ruleRemediationMappingRepository.findAll()).thenReturn(List.of(globalMapping, tenantMapping));
        when(policyChangeAuditRepository.findAll()).thenReturn(List.of(new PolicyChangeAudit(), new PolicyChangeAudit()));

        PolicyTemplateApplyReport report = service.applyProductionPack("admin", true, true).block();

        assertNotNull(report);
        assertEquals(PolicyTemplateMaintenanceService.PACK_NAME, report.packName());
        assertEquals("admin", report.actor());
        assertTrue(report.includeTenantScopes());
        assertTrue(report.clearPolicyAudit());
        assertEquals(2, report.clearedPolicyAuditRows());
        assertEquals(2, report.retiredSystemRules());
        assertEquals(2, report.retiredSystemRuleConditions());
        assertEquals(2, report.retiredRejectApps());
        assertEquals(2, report.retiredTrustScorePolicies());
        assertEquals(2, report.retiredTrustDecisionPolicies());
        assertEquals(2, report.retiredRemediationRules());
        assertEquals(2, report.retiredRuleRemediationMappings());
        assertEquals(10, report.appliedSystemRules());
        assertEquals(10, report.appliedSystemRuleConditions());
        assertEquals(2, report.appliedRejectApps());
        assertEquals(14, report.appliedTrustScorePolicies());
        assertEquals(4, report.appliedTrustDecisionPolicies());
        assertEquals(8, report.appliedRemediationRules());
        assertEquals(15, report.appliedRuleRemediationMappings());

        assertTrue(globalRule.isDeleted());
        assertTrue(tenantRule.isDeleted());
        assertTrue(globalCondition.isDeleted());
        assertTrue(tenantCondition.isDeleted());
        assertTrue(globalReject.isDeleted());
        assertTrue(tenantReject.isDeleted());

        verify(policyChangeAuditRepository).deleteAll();
        verify(remediationRuleRepository, times(10)).save(any(RemediationRule.class));
        verify(systemRuleRepository, times(12)).save(any(SystemInformationRule.class));
        verify(conditionRepository, times(12)).save(any(SystemInformationRuleCondition.class));
        verify(rejectApplicationRepository, times(4)).save(any(RejectApplication.class));
        verify(trustScorePolicyRepository, times(16)).save(any(TrustScorePolicy.class));
        verify(trustScoreDecisionPolicyRepository, times(6)).save(any(TrustScoreDecisionPolicy.class));
        verify(ruleRemediationMappingRepository, times(17)).save(any(RuleRemediationMapping.class));
    }

    @Test
    void applyProductionPack_globalOnlyLeavesTenantPoliciesUntouched() {
        SystemInformationRule tenantRule = rule(2L, "TEST_TENANT_RULE", "tenant-a");
        SystemInformationRuleCondition tenantCondition = condition(12L, 2L);
        when(systemRuleRepository.findAll()).thenReturn(List.of(tenantRule));
        when(conditionRepository.findAll()).thenReturn(List.of(tenantCondition));
        when(rejectApplicationRepository.findAll()).thenReturn(List.of());
        when(trustScorePolicyRepository.findAll()).thenReturn(List.of());
        when(trustScoreDecisionPolicyRepository.findAll()).thenReturn(List.of());
        when(remediationRuleRepository.findAll()).thenReturn(List.of());
        when(ruleRemediationMappingRepository.findAll()).thenReturn(List.of());

        PolicyTemplateApplyReport report = service.applyProductionPack("admin", false, false).block();

        assertNotNull(report);
        assertFalse(report.includeTenantScopes());
        assertEquals(0, report.retiredSystemRules());
        assertEquals(0, report.retiredSystemRuleConditions());
        assertFalse(tenantRule.isDeleted());
        assertFalse(tenantCondition.isDeleted());
        assertEquals(10, report.appliedSystemRules());
        assertEquals(8, report.appliedRemediationRules());
    }

    @Test
    void applyProductionPack_seedsDevicePoliciesForEverySupportedOsType() {
        when(systemRuleRepository.findAll()).thenReturn(List.of());
        when(conditionRepository.findAll()).thenReturn(List.of());
        when(rejectApplicationRepository.findAll()).thenReturn(List.of());
        when(trustScorePolicyRepository.findAll()).thenReturn(List.of());
        when(trustScoreDecisionPolicyRepository.findAll()).thenReturn(List.of());
        when(remediationRuleRepository.findAll()).thenReturn(List.of());
        when(ruleRemediationMappingRepository.findAll()).thenReturn(List.of());

        PolicyTemplateApplyReport report = service.applyProductionPack("admin", false, false).block();

        assertNotNull(report);
        assertEquals(10, report.appliedSystemRules());

        ArgumentCaptor<SystemInformationRule> captor = ArgumentCaptor.forClass(SystemInformationRule.class);
        verify(systemRuleRepository, times(10)).save(captor.capture());
        Set<String> osTypes = captor.getAllValues().stream()
                .map(SystemInformationRule::getOsType)
                .collect(Collectors.toSet());

        assertEquals(Set.of("ANDROID", "IOS", "WINDOWS", "MACOS", "LINUX", "CHROMEOS", "FREEBSD", "OPENBSD"), osTypes);
    }

    private SystemInformationRule assignRuleId(SystemInformationRule rule, AtomicLong ids) {
        if (rule.getId() == null) {
            rule.setId(ids.getAndIncrement());
        }
        return rule;
    }

    private SystemInformationRuleCondition assignConditionId(SystemInformationRuleCondition condition, AtomicLong ids) {
        if (condition.getId() == null) {
            condition.setId(ids.getAndIncrement());
        }
        return condition;
    }

    private RejectApplication assignRejectId(RejectApplication app, AtomicLong ids) {
        if (app.getId() == null) {
            app.setId(ids.getAndIncrement());
        }
        return app;
    }

    private TrustScorePolicy assignTrustPolicyId(TrustScorePolicy policy, AtomicLong ids) {
        if (policy.getId() == null) {
            policy.setId(ids.getAndIncrement());
        }
        return policy;
    }

    private TrustScoreDecisionPolicy assignDecisionId(TrustScoreDecisionPolicy policy, AtomicLong ids) {
        if (policy.getId() == null) {
            policy.setId(ids.getAndIncrement());
        }
        return policy;
    }

    private RemediationRule assignRemediationId(RemediationRule rule, AtomicLong ids) {
        if (rule.getId() == null) {
            rule.setId(ids.getAndIncrement());
        }
        return rule;
    }

    private RuleRemediationMapping assignMappingId(RuleRemediationMapping mapping, AtomicLong ids) {
        if (mapping.getId() == null) {
            mapping.setId(ids.getAndIncrement());
        }
        return mapping;
    }

    private SystemInformationRule rule(Long id, String code, String tenantId) {
        SystemInformationRule rule = new SystemInformationRule();
        rule.setId(id);
        rule.setRuleCode(code);
        rule.setRuleTag(code);
        rule.setTenantId(tenantId);
        rule.setStatus("ACTIVE");
        rule.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return rule;
    }

    private SystemInformationRuleCondition condition(Long id, Long ruleId) {
        SystemInformationRuleCondition condition = new SystemInformationRuleCondition();
        condition.setId(id);
        condition.setSystemInformationRuleId(ruleId);
        condition.setFieldName("root_detected");
        condition.setOperator("EQ");
        condition.setValueBoolean(Boolean.TRUE);
        condition.setStatus("ACTIVE");
        return condition;
    }

    private RejectApplication rejectApp(Long id, String appName, String tenantId) {
        RejectApplication app = new RejectApplication();
        app.setId(id);
        app.setPolicyTag("TEST_APP");
        app.setSeverity((short) 3);
        app.setAppName(appName);
        app.setAppOsType("WINDOWS");
        app.setTenantId(tenantId);
        app.setStatus("ACTIVE");
        app.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return app;
    }

    private TrustScorePolicy trustPolicy(Long id, String code, String tenantId) {
        TrustScorePolicy policy = new TrustScorePolicy();
        policy.setId(id);
        policy.setPolicyCode(code);
        policy.setSourceType("SYSTEM_RULE");
        policy.setSignalKey(code);
        policy.setScoreDelta((short) -10);
        policy.setWeight(1.0);
        policy.setTenantId(tenantId);
        policy.setStatus("ACTIVE");
        policy.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return policy;
    }

    private TrustScoreDecisionPolicy decisionPolicy(Long id, String name, String tenantId) {
        TrustScoreDecisionPolicy policy = new TrustScoreDecisionPolicy();
        policy.setId(id);
        policy.setPolicyName(name);
        policy.setScoreMin((short) 0);
        policy.setScoreMax((short) 100);
        policy.setDecisionAction("ALLOW");
        policy.setTenantId(tenantId);
        policy.setStatus("ACTIVE");
        policy.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return policy;
    }

    private RemediationRule remediation(Long id, String code, String tenantId) {
        RemediationRule rule = new RemediationRule();
        rule.setId(id);
        rule.setRemediationCode(code);
        rule.setTitle(code);
        rule.setTenantId(tenantId);
        rule.setStatus("ACTIVE");
        rule.setPriority((short) 10);
        rule.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return rule;
    }

    private RuleRemediationMapping mapping(Long id, Long ruleId, Long remediationId, String tenantId) {
        RuleRemediationMapping mapping = new RuleRemediationMapping();
        mapping.setId(id);
        mapping.setSourceType("SYSTEM_RULE");
        mapping.setSystemInformationRuleId(ruleId);
        mapping.setRemediationRuleId(remediationId);
        mapping.setTenantId(tenantId);
        mapping.setStatus("ACTIVE");
        mapping.setEnforceMode("MANUAL");
        mapping.setRankOrder((short) 1);
        mapping.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return mapping;
    }
}
