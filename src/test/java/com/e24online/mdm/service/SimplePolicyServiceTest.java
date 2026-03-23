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
import com.e24online.mdm.web.dto.SimpleAppPolicySummary;
import com.e24online.mdm.web.dto.SimpleDevicePolicySummary;
import com.e24online.mdm.web.dto.SimplePolicyStarterPackSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimplePolicyServiceTest {

    @Mock
    private SystemInformationRuleRepository systemRuleRepository;
    @Mock
    private SystemInformationRuleConditionRepository conditionRepository;
    @Mock
    private RejectApplicationRepository rejectApplicationRepository;
    @Mock
    private RuleRemediationMappingRepository ruleRemediationMappingRepository;
    @Mock
    private RemediationRuleRepository remediationRuleRepository;
    @Mock
    private TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    @Mock
    private PolicyChangeAuditRepository policyChangeAuditRepository;
    @Mock
    private PolicyAuditPublisher policyAuditPublisher;
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SimplePolicyService service;

    @BeforeEach
    void setUp() {
        service = new SimplePolicyService(
                systemRuleRepository,
                conditionRepository,
                rejectApplicationRepository,
                ruleRemediationMappingRepository,
                remediationRuleRepository,
                trustScoreDecisionPolicyRepository,
                policyChangeAuditRepository,
                policyAuditPublisher,
                auditEventService,
                new BlockingDb(Schedulers.immediate()),
                transactionTemplate,
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );

        AtomicLong ids = new AtomicLong(100);
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(remediationRuleRepository.save(any(RemediationRule.class))).thenAnswer(invocation -> {
            RemediationRule rule = invocation.getArgument(0);
            if (rule.getId() == null) rule.setId(ids.getAndIncrement());
            return rule;
        });
        lenient().when(systemRuleRepository.save(any(SystemInformationRule.class))).thenAnswer(invocation -> {
            SystemInformationRule rule = invocation.getArgument(0);
            if (rule.getId() == null) rule.setId(ids.getAndIncrement());
            return rule;
        });
        lenient().when(rejectApplicationRepository.save(any(RejectApplication.class))).thenAnswer(invocation -> {
            RejectApplication app = invocation.getArgument(0);
            if (app.getId() == null) app.setId(ids.getAndIncrement());
            return app;
        });
        lenient().when(conditionRepository.save(any(SystemInformationRuleCondition.class))).thenAnswer(invocation -> {
            SystemInformationRuleCondition rule = invocation.getArgument(0);
            if (rule.getId() == null) rule.setId(ids.getAndIncrement());
            return rule;
        });
        lenient().when(ruleRemediationMappingRepository.save(any(RuleRemediationMapping.class))).thenAnswer(invocation -> {
            RuleRemediationMapping rule = invocation.getArgument(0);
            if (rule.getId() == null) rule.setId(ids.getAndIncrement());
            return rule;
        });
        lenient().when(trustScoreDecisionPolicyRepository.save(any(TrustScoreDecisionPolicy.class))).thenAnswer(invocation -> {
            TrustScoreDecisionPolicy rule = invocation.getArgument(0);
            if (rule.getId() == null) rule.setId(ids.getAndIncrement());
            return rule;
        });
        lenient().when(policyChangeAuditRepository.save(any(PolicyChangeAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void installStarterPack_createsBaselinePoliciesForEmptyScope() {
        when(remediationRuleRepository.findPaged(null, null, 2000, 0L)).thenReturn(List.of());
        when(systemRuleRepository.findPaged(null, null, 2000, 0L)).thenReturn(List.of());
        when(rejectApplicationRepository.findPaged(null, null, null, 2000, 0L)).thenReturn(List.of());
        when(trustScoreDecisionPolicyRepository.findPaged(null, null, 2000, 0L)).thenReturn(List.of());
        when(ruleRemediationMappingRepository.findPaged(null, "SYSTEM_RULE", 2000, 0L)).thenReturn(List.of());

        SimplePolicyStarterPackSummary summary = service.installStarterPack("actor", "PRODUCT_ADMIN", null).block();

        assertNotNull(summary);
        assertEquals("global", summary.getScope());
        assertEquals(8, summary.getCreatedFixes());
        assertEquals(10, summary.getCreatedDeviceChecks());
        assertEquals(4, summary.getCreatedTrustLevels());
        assertEquals(2, summary.getCreatedAppRules());
        assertEquals(0, summary.getSkippedFixes());
        assertEquals(0, summary.getSkippedDeviceChecks());
        assertEquals(0, summary.getSkippedAppRules());
        assertEquals(0, summary.getSkippedTrustLevels());

        verify(remediationRuleRepository, times(8)).save(any(RemediationRule.class));
        verify(systemRuleRepository, times(10)).save(any(SystemInformationRule.class));
        verify(rejectApplicationRepository, times(2)).save(any(RejectApplication.class));
        verify(conditionRepository, times(10)).save(any(SystemInformationRuleCondition.class));
        verify(ruleRemediationMappingRepository, times(12)).save(any(RuleRemediationMapping.class));
        verify(trustScoreDecisionPolicyRepository, times(4)).save(any(TrustScoreDecisionPolicy.class));
    }

    @Test
    void installStarterPack_skipsAlreadyReadableStarterItems() {
        RejectApplication anyDesk = rejectApplication("Block AnyDesk remote admin tool", "tenant-a");
        anyDesk.setAppOsType("WINDOWS");
        anyDesk.setAppName("AnyDesk");
        anyDesk.setPackageId("AnyDeskSoftwareGmbH.AnyDesk");
        anyDesk.setMinAllowedVersion("999999.0.0");

        RejectApplication teamViewer = rejectApplication("Block TeamViewer remote admin tool", "tenant-a");
        teamViewer.setId(2L);
        teamViewer.setAppOsType("WINDOWS");
        teamViewer.setAppName("TeamViewer");
        teamViewer.setMinAllowedVersion("999999.0.0");

        List<RemediationRule> tenantFixes = List.of(
                remediationRule("Remove root access and rescan"),
                remediationRule("Remove jailbreak access and rescan"),
                remediationRule("Turn off USB debugging"),
                remediationRule("Stop using an emulated device"),
                remediationRule("Move the workload to a supported physical device"),
                remediationRule("Remove the blocked remote admin tool"),
                remediationRule("Update the device to a supported OS release"),
                remediationRule("Move the device off the unsupported OS release")
        );
        tenantFixes.forEach(rule -> rule.setTenantId("tenant-a"));

        List<SystemInformationRule> tenantRules = List.of(
                systemRule("Rooted Android device"),
                systemRule("Jailbroken iOS device"),
                systemRule("USB debugging enabled"),
                systemRule("Running on emulator"),
                systemRule("Virtualized Windows device"),
                systemRule("Virtualized macOS device"),
                systemRule("Virtualized Linux device"),
                systemRule("Virtualized ChromeOS device"),
                systemRule("Virtualized FreeBSD device"),
                systemRule("Virtualized OpenBSD device")
        );
        tenantRules.forEach(rule -> rule.setTenantId("tenant-a"));

        TrustScoreDecisionPolicy allow = trustLevel("Allow", (short) 80, (short) 100, "ALLOW");
        allow.setTenantId("tenant-a");
        TrustScoreDecisionPolicy notify = trustLevel("Notify", (short) 60, (short) 79, "NOTIFY");
        notify.setId(2L);
        notify.setTenantId("tenant-a");
        TrustScoreDecisionPolicy quarantine = trustLevel("Quarantine", (short) 40, (short) 59, "QUARANTINE");
        quarantine.setId(3L);
        quarantine.setTenantId("tenant-a");
        TrustScoreDecisionPolicy block = trustLevel("Block", (short) 0, (short) 39, "BLOCK");
        block.setId(4L);
        block.setTenantId("tenant-a");

        when(remediationRuleRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(tenantFixes);
        when(systemRuleRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(tenantRules);
        when(rejectApplicationRepository.findPaged("tenant-a", null, null, 2000, 0L)).thenReturn(List.of(
                anyDesk,
                teamViewer
        ));
        when(trustScoreDecisionPolicyRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(List.of(
                allow,
                notify,
                quarantine,
                block
        ));

        SimplePolicyStarterPackSummary summary = service.installStarterPack("actor", "TENANT_ADMIN", "tenant-a").block();

        assertNotNull(summary);
        assertEquals("tenant-a", summary.getScope());
        assertEquals(0, summary.getCreatedFixes());
        assertEquals(0, summary.getCreatedDeviceChecks());
        assertEquals(0, summary.getCreatedAppRules());
        assertEquals(0, summary.getCreatedTrustLevels());
        assertEquals(8, summary.getSkippedFixes());
        assertEquals(10, summary.getSkippedDeviceChecks());
        assertEquals(2, summary.getSkippedAppRules());
        assertEquals(4, summary.getSkippedTrustLevels());

        verify(remediationRuleRepository, times(0)).save(any(RemediationRule.class));
        verify(systemRuleRepository, times(0)).save(any(SystemInformationRule.class));
        verify(trustScoreDecisionPolicyRepository, times(0)).save(any(TrustScoreDecisionPolicy.class));
    }

    @Test
    void installStarterPack_tenantScopeCreatesOwnedStarterItemsEvenWhenGlobalStarterItemsExist() {
        List<RemediationRule> globalFixes = List.of(
                remediationRule("Remove root access and rescan"),
                remediationRule("Remove jailbreak access and rescan"),
                remediationRule("Turn off USB debugging"),
                remediationRule("Stop using an emulated device"),
                remediationRule("Move the workload to a supported physical device"),
                remediationRule("Remove the blocked remote admin tool"),
                remediationRule("Update the device to a supported OS release"),
                remediationRule("Move the device off the unsupported OS release")
        );
        globalFixes.forEach(rule -> rule.setTenantId(null));

        List<SystemInformationRule> globalRules = List.of(
                systemRule("Rooted Android device"),
                systemRule("Jailbroken iOS device"),
                systemRule("USB debugging enabled"),
                systemRule("Running on emulator"),
                systemRule("Virtualized Windows device"),
                systemRule("Virtualized macOS device"),
                systemRule("Virtualized Linux device"),
                systemRule("Virtualized ChromeOS device"),
                systemRule("Virtualized FreeBSD device"),
                systemRule("Virtualized OpenBSD device")
        );
        globalRules.forEach(rule -> rule.setTenantId(null));

        RejectApplication globalAnyDesk = rejectApplication("Block AnyDesk remote admin tool", null);
        globalAnyDesk.setAppOsType("WINDOWS");
        globalAnyDesk.setAppName("AnyDesk");
        globalAnyDesk.setPackageId("AnyDeskSoftwareGmbH.AnyDesk");
        globalAnyDesk.setMinAllowedVersion("999999.0.0");

        RejectApplication globalTeamViewer = rejectApplication("Block TeamViewer remote admin tool", null);
        globalTeamViewer.setId(2L);
        globalTeamViewer.setAppOsType("WINDOWS");
        globalTeamViewer.setAppName("TeamViewer");
        globalTeamViewer.setMinAllowedVersion("999999.0.0");

        TrustScoreDecisionPolicy globalAllow = trustLevel("Allow", (short) 80, (short) 100, "ALLOW");
        globalAllow.setTenantId(null);
        TrustScoreDecisionPolicy globalNotify = trustLevel("Notify", (short) 60, (short) 79, "NOTIFY");
        globalNotify.setId(2L);
        globalNotify.setTenantId(null);
        TrustScoreDecisionPolicy globalQuarantine = trustLevel("Quarantine", (short) 40, (short) 59, "QUARANTINE");
        globalQuarantine.setId(3L);
        globalQuarantine.setTenantId(null);
        TrustScoreDecisionPolicy globalBlock = trustLevel("Block", (short) 0, (short) 39, "BLOCK");
        globalBlock.setId(4L);
        globalBlock.setTenantId(null);

        when(remediationRuleRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(globalFixes);
        when(systemRuleRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(globalRules);
        when(rejectApplicationRepository.findPaged("tenant-a", null, null, 2000, 0L)).thenReturn(List.of(globalAnyDesk, globalTeamViewer));
        when(trustScoreDecisionPolicyRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(List.of(globalAllow, globalNotify, globalQuarantine, globalBlock));

        SimplePolicyStarterPackSummary summary = service.installStarterPack("actor", "TENANT_ADMIN", "tenant-a").block();

        assertNotNull(summary);
        assertEquals("tenant-a", summary.getScope());
        assertEquals(8, summary.getCreatedFixes());
        assertEquals(10, summary.getCreatedDeviceChecks());
        assertEquals(2, summary.getCreatedAppRules());
        assertEquals(4, summary.getCreatedTrustLevels());
        assertEquals(0, summary.getSkippedFixes());
        assertEquals(0, summary.getSkippedDeviceChecks());
        assertEquals(0, summary.getSkippedAppRules());
        assertEquals(0, summary.getSkippedTrustLevels());
    }

    @Test
    void tenantLists_hideGlobalPolicies_butKeepGlobalFixOptionsReadable() {
        SystemInformationRule globalRule = systemRule("Global root rule");
        globalRule.setTenantId(null);
        globalRule.setSeverity((short) 5);
        globalRule.setStatus("ACTIVE");
        globalRule.setRuleCode("global-rule");
        globalRule.setOsType("ANDROID");
        globalRule.setCreatedAt(OffsetDateTime.now().minusDays(2));

        SystemInformationRule tenantRule = systemRule("Tenant root rule");
        tenantRule.setId(2L);
        tenantRule.setTenantId("tenant-a");
        tenantRule.setSeverity((short) 4);
        tenantRule.setStatus("ACTIVE");
        tenantRule.setRuleCode("tenant-rule");
        tenantRule.setOsType("ANDROID");
        tenantRule.setCreatedAt(OffsetDateTime.now().minusDays(1));

        RejectApplication globalApp = rejectApplication("Global blocked app", null);
        RejectApplication tenantApp = rejectApplication("Tenant blocked app", "tenant-a");

        TrustScoreDecisionPolicy globalTrust = trustLevel("Global block", (short) 0, (short) 39, "BLOCK");
        globalTrust.setTenantId(null);
        TrustScoreDecisionPolicy tenantTrust = trustLevel("Tenant block", (short) 0, (short) 39, "BLOCK");
        tenantTrust.setId(2L);
        tenantTrust.setTenantId("tenant-a");

        RemediationRule globalFix = remediationRule("Global fix");
        globalFix.setTenantId(null);
        globalFix.setStatus("ACTIVE");
        globalFix.setRemediationCode("global-fix");
        RemediationRule tenantFix = remediationRule("Tenant fix");
        tenantFix.setId(2L);
        tenantFix.setTenantId("tenant-a");
        tenantFix.setStatus("ACTIVE");
        tenantFix.setRemediationCode("tenant-fix");

        when(systemRuleRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(List.of(globalRule, tenantRule));
        when(conditionRepository.findByRuleId(2L)).thenReturn(List.of());
        when(rejectApplicationRepository.findPaged("tenant-a", null, null, 2000, 0L)).thenReturn(List.of(globalApp, tenantApp));
        when(ruleRemediationMappingRepository.findPaged("tenant-a", "SYSTEM_RULE", 2000, 0L)).thenReturn(List.of());
        when(ruleRemediationMappingRepository.findPaged("tenant-a", "REJECT_APPLICATION", 2000, 0L)).thenReturn(List.of());
        when(trustScoreDecisionPolicyRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(List.of(globalTrust, tenantTrust));
        when(remediationRuleRepository.findPaged("tenant-a", null, 2000, 0L)).thenReturn(List.of(globalFix, tenantFix));
        when(remediationRuleRepository.findPaged("tenant-a", "ACTIVE", 2000, 0L)).thenReturn(List.of(globalFix, tenantFix));

        assertEquals(List.of("Tenant root rule"), service.listDevicePolicies("TENANT_ADMIN", "tenant-a")
                .map(SimpleDevicePolicySummary::getName)
                .collectList()
                .block());
        assertEquals(List.of("Tenant blocked app"), service.listAppPolicies("TENANT_ADMIN", "tenant-a")
                .map(SimpleAppPolicySummary::getPolicyTag)
                .collectList()
                .block());
        assertEquals(List.of("Tenant block"), service.listTrustLevels("TENANT_ADMIN", "tenant-a")
                .map(TrustScoreDecisionPolicy::getPolicyName)
                .collectList()
                .block());
        assertEquals(List.of("Tenant fix"), service.listFixLibrary("TENANT_ADMIN", "tenant-a")
                .map(RemediationRule::getTitle)
                .collectList()
                .block());
        assertEquals(List.of("Tenant fix", "Global fix"), service.listFixOptions("TENANT_ADMIN", "tenant-a")
                .map(RemediationRule::getTitle)
                .collectList()
                .block());
    }

    private RemediationRule remediationRule(String title) {
        RemediationRule rule = new RemediationRule();
        rule.setId(1L);
        rule.setTitle(title);
        rule.setCreatedAt(OffsetDateTime.now().minusDays(1));
        return rule;
    }

    private RejectApplication rejectApplication(String policyTag, String tenantId) {
        RejectApplication app = new RejectApplication();
        app.setId(1L);
        app.setPolicyTag(policyTag);
        app.setTenantId(tenantId);
        app.setAppName(policyTag);
        app.setAppOsType("ANDROID");
        app.setSeverity((short) 4);
        app.setMinAllowedVersion("1.0.0");
        app.setStatus("ACTIVE");
        app.setCreatedAt(OffsetDateTime.now().minusDays(1));
        return app;
    }

    private SystemInformationRule systemRule(String tag) {
        SystemInformationRule rule = new SystemInformationRule();
        rule.setId(1L);
        rule.setRuleTag(tag);
        rule.setCreatedAt(OffsetDateTime.now().minusDays(1));
        return rule;
    }

    private TrustScoreDecisionPolicy trustLevel(String name, short scoreMin, short scoreMax, String action) {
        TrustScoreDecisionPolicy policy = new TrustScoreDecisionPolicy();
        policy.setId(1L);
        policy.setPolicyName(name);
        policy.setScoreMin(scoreMin);
        policy.setScoreMax(scoreMax);
        policy.setDecisionAction(action);
        return policy;
    }
}
