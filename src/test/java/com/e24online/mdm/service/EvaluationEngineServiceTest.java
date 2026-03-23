package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.repository.DeviceTrustScoreEventRepository;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import com.e24online.mdm.repository.TrustScorePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationEngineServiceTest {

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
    private DeviceTrustScoreEventRepository scoreEventRepository;

    private ObjectMapper objectMapper;
    private EvaluationEngineService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new EvaluationEngineService(
                systemRuleRepository,
                conditionRepository,
                rejectApplicationRepository,
                trustScorePolicyRepository,
                trustScoreDecisionPolicyRepository,
                scoreEventRepository,
                objectMapper,
                reactor.core.scheduler.Schedulers.immediate()
        );
    }

    @Test
    void computeEvaluation_appliesRulesRejectAppsLifecycleAndDecisionPolicy() {
        OffsetDateTime now = OffsetDateTime.now();
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setCurrentScore((short) 90);

        ParsedPosture parsed = posture("SOLARIS", "WORKSTATION", 33, "Acme Inc");
        DeviceInstalledApplication app = new DeviceInstalledApplication();
        app.setId(701L);
        app.setAppName("Malware");
        app.setPackageId("bad.pkg");
        app.setAppOsType("SOLARIS");
        app.setAppVersion("1.0.0");

        SystemInformationRule rule = new SystemInformationRule();
        rule.setId(11L);
        rule.setDeleted(false);
        rule.setStatus("ACTIVE");
        rule.setOsType("SOLARIS");
        rule.setOsName("SOLARIS 11");
        rule.setDeviceType("WORKSTATION");
        rule.setMatchMode("ANY");
        rule.setRuleCode("RULE_CODE_A");
        rule.setRuleTag("RULE_TAG_A");
        rule.setSeverity((short) 5);
        rule.setComplianceAction("QUARANTINE");
        rule.setRiskScoreDelta((short) -8);
        rule.setPriority(1);

        SystemInformationRuleCondition c1 = new SystemInformationRuleCondition();
        c1.setSystemInformationRuleId(11L);
        c1.setDeleted(false);
        c1.setStatus("ACTIVE");
        c1.setConditionGroup((short) 1);
        c1.setFieldName("manufacturer");
        c1.setOperator("REGEX");
        c1.setValueText("Acme.*");

        SystemInformationRuleCondition c2 = new SystemInformationRuleCondition();
        c2.setSystemInformationRuleId(11L);
        c2.setDeleted(false);
        c2.setStatus("ACTIVE");
        c2.setConditionGroup((short) 2);
        c2.setFieldName("api_level");
        c2.setOperator("GT");
        c2.setValueNumeric(40.0d); // false, but ANY mode still passes due group1

        RejectApplication reject = new RejectApplication();
        reject.setId(22L);
        reject.setDeleted(false);
        reject.setStatus("ACTIVE");
        reject.setAppOsType("SOLARIS");
        reject.setPackageId("bad.pkg");
        reject.setAppName("Malware");
        reject.setPolicyTag("BLOCKLIST-A");
        reject.setSeverity((short) 4);
        reject.setMinAllowedVersion("2.0.0");

        TrustScorePolicy rulePolicy = new TrustScorePolicy();
        rulePolicy.setId(101L);
        rulePolicy.setDeleted(false);
        rulePolicy.setStatus("ACTIVE");
        rulePolicy.setSourceType("SYSTEM_RULE");
        rulePolicy.setSignalKey("RULE_CODE_A");
        rulePolicy.setSeverity((short) 5);
        rulePolicy.setComplianceAction("QUARANTINE");
        rulePolicy.setScoreDelta((short) -8);
        rulePolicy.setWeight(1.5d); // weighted to -12

        TrustScorePolicy rejectPolicy = new TrustScorePolicy();
        rejectPolicy.setId(102L);
        rejectPolicy.setDeleted(false);
        rejectPolicy.setStatus("ACTIVE");
        rejectPolicy.setSourceType("REJECT_APPLICATION");
        rejectPolicy.setSignalKey("bad.pkg");
        rejectPolicy.setSeverity((short) 4);
        rejectPolicy.setComplianceAction("BLOCK");
        rejectPolicy.setScoreDelta((short) -20);
        rejectPolicy.setWeight(1.0d);

        TrustScorePolicy lifecyclePolicy = new TrustScorePolicy();
        lifecyclePolicy.setId(103L);
        lifecyclePolicy.setDeleted(false);
        lifecyclePolicy.setStatus("ACTIVE");
        lifecyclePolicy.setSourceType("POSTURE_SIGNAL");
        lifecyclePolicy.setSignalKey("OS_EEOL");
        lifecyclePolicy.setScoreDelta((short) -15);
        lifecyclePolicy.setWeight(1.0d);

        TrustScoreDecisionPolicy decisionPolicy = new TrustScoreDecisionPolicy();
        decisionPolicy.setId(200L);
        decisionPolicy.setDecisionAction("BLOCK");
        decisionPolicy.setRemediationRequired(true);
        decisionPolicy.setResponseMessage("Policy decision");

        when(systemRuleRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(rule));
        when(conditionRepository.findActiveByRuleIds(anyList())).thenReturn(List.of(c1, c2));
        when(rejectApplicationRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(reject));
        when(trustScorePolicyRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(rulePolicy, rejectPolicy, lifecyclePolicy));
        when(trustScoreDecisionPolicyRepository.findActivePolicyForScore(anyString(), anyInt(), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(decisionPolicy));

        EvaluationComputation result = service.computeEvaluation(
                profile,
                parsed,
                List.of(app),
                new LifecycleResolution(9L, "EEOL", "OS_EEOL"),
                now
        );

        assertNotNull(result);
        assertEquals(90, result.scoreBefore());
        assertEquals(43, result.scoreAfter());
        assertEquals(-47, result.scoreDeltaTotal());
        assertEquals(1, result.matchedRuleCount());
        assertEquals(1, result.matchedAppCount());
        assertEquals("BLOCK", result.decisionAction());
        assertTrue(result.remediationRequired());
        assertEquals(200L, result.decisionPolicyId());
        assertEquals(3, result.matches().size());
        assertEquals(3, result.scoreSignals().size());
    }

    @Test
    void computeEvaluation_withoutPolicies_usesDefaultDecisionAndSignals() {
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setCurrentScore((short) 55);
        ParsedPosture parsed = posture("SOLARIS", "WORKSTATION", 30, "Acme");

        when(systemRuleRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(rejectApplicationRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(trustScorePolicyRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(trustScoreDecisionPolicyRepository.findActivePolicyForScore(anyString(), anyInt(), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        EvaluationComputation result = service.computeEvaluation(
                profile,
                parsed,
                List.of(),
                new LifecycleResolution(null, "SUPPORTED", "OS_SUPPORTED"),
                OffsetDateTime.now()
        );

        assertEquals(55, result.scoreBefore());
        assertEquals(55, result.scoreAfter());
        assertEquals(0, result.scoreDeltaTotal());
        assertEquals("QUARANTINE", result.decisionAction());
        assertEquals(0, result.matches().size());
        assertEquals(0, result.scoreSignals().size());
        assertTrue(result.remediationRequired()); // non-ALLOW defaults to remediation required
    }

    @Test
    void computeEvaluation_handlesNullSignalCandidatesWithoutCrashing() {
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setCurrentScore((short) 100);
        ParsedPosture parsed = posture("WINDOWS", "WORKSTATION", 30, "Acme");

        DeviceInstalledApplication app = new DeviceInstalledApplication();
        app.setId(801L);
        app.setAppName("Remote Tool");
        app.setPackageId(null);
        app.setAppOsType("WINDOWS");
        app.setAppVersion("1.0.0");

        SystemInformationRule rule = new SystemInformationRule();
        rule.setId(31L);
        rule.setDeleted(false);
        rule.setStatus("ACTIVE");
        rule.setOsType("WINDOWS");
        rule.setDeviceType("WORKSTATION");
        rule.setRuleCode("WINDOWS_VM");
        rule.setRuleTag(null);
        rule.setSeverity((short) 2);
        rule.setComplianceAction("NOTIFY");
        rule.setRiskScoreDelta((short) -5);

        RejectApplication reject = new RejectApplication();
        reject.setId(32L);
        reject.setDeleted(false);
        reject.setStatus("ACTIVE");
        reject.setAppOsType("WINDOWS");
        reject.setPackageId(null);
        reject.setAppName("Remote Tool");
        reject.setPolicyTag(null);
        reject.setSeverity((short) 3);
        reject.setMinAllowedVersion(null);

        when(systemRuleRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(rule));
        when(conditionRepository.findActiveByRuleIds(anyList())).thenReturn(List.of());
        when(rejectApplicationRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(reject));
        when(trustScorePolicyRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of());
        when(trustScoreDecisionPolicyRepository.findActivePolicyForScore(anyString(), anyInt(), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        EvaluationComputation result = service.computeEvaluation(
                profile,
                parsed,
                List.of(app),
                new LifecycleResolution(5L, "NOT_TRACKED", null),
                OffsetDateTime.now()
        );

        assertNotNull(result);
        assertEquals(1, result.matchedRuleCount());
        assertEquals(1, result.matchedAppCount());
        assertEquals(3, result.scoreSignals().size());
        assertEquals(2, result.matches().size());
        assertEquals(50, result.scoreAfter());
        assertEquals("QUARANTINE", result.decisionAction());
    }

    private ParsedPosture posture(String osType, String deviceType, int apiLevel, String manufacturer) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("manufacturer", manufacturer);
        root.put("api_level", apiLevel);
        ArrayNode apps = objectMapper.createArrayNode();
        return new ParsedPosture(
                "tenant-a",
                "dev-1",
                "agent-1",
                osType,
                osType + " 11",
                "11.0.0",
                "11",
                deviceType,
                "UTC",
                "1.0",
                apiLevel,
                "19045",
                manufacturer,
                false,
                false,
                false,
                OffsetDateTime.now(),
                root,
                apps
        );
    }
}
