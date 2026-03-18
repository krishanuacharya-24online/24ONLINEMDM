package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.domain.DeviceSystemSnapshot;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.domain.DeviceTrustScoreEvent;
import com.e24online.mdm.domain.OsReleaseLifecycleMaster;
import com.e24online.mdm.domain.PostureEvaluationMatch;
import com.e24online.mdm.domain.PostureEvaluationRemediation;
import com.e24online.mdm.domain.PostureEvaluationRun;
import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.RuleRemediationMapping;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DeviceInstalledApplicationRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.DeviceSystemSnapshotRepository;
import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.DeviceTrustScoreEventRepository;
import com.e24online.mdm.repository.OsReleaseLifecycleMasterRepository;
import com.e24online.mdm.repository.PostureEvaluationMatchRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.repository.PostureEvaluationRunRepository;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import com.e24online.mdm.repository.TrustScorePolicyRepository;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPostureWorkflowServiceTest {

    @Mock
    private PostureIngestionService ingestService;
    @Mock
    private DevicePosturePayloadRepository payloadRepository;
    @Mock
    private DeviceTrustProfileRepository profileRepository;
    @Mock
    private DeviceSystemSnapshotRepository snapshotRepository;
    @Mock
    private DeviceInstalledApplicationRepository installedApplicationRepository;
    @Mock
    private DeviceTrustScoreEventRepository scoreEventRepository;
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
    private RuleRemediationMappingRepository ruleRemediationMappingRepository;
    @Mock
    private RemediationRuleRepository remediationRuleRepository;
    @Mock
    private PostureEvaluationRunRepository runRepository;
    @Mock
    private PostureEvaluationMatchRepository matchRepository;
    @Mock
    private PostureEvaluationRemediationRepository remediationRepository;
    @Mock
    private DeviceDecisionResponseRepository decisionRepository;
    @Mock
    private OsReleaseLifecycleMasterRepository osLifecycleRepository;
    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private AuditEventService auditEventService;

    private ObjectMapper objectMapper;
    private AgentPostureWorkflowService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AgentPostureWorkflowService(
                ingestService,
                payloadRepository,
                profileRepository,
                snapshotRepository,
                installedApplicationRepository,
                scoreEventRepository,
                systemRuleRepository,
                conditionRepository,
                rejectApplicationRepository,
                trustScorePolicyRepository,
                trustScoreDecisionPolicyRepository,
                ruleRemediationMappingRepository,
                remediationRuleRepository,
                runRepository,
                matchRepository,
                remediationRepository,
                decisionRepository,
                osLifecycleRepository,
                auditEventService,
                objectMapper,
                new BlockingDb(Schedulers.immediate()),
                jdbc
        );

        lenient().when(payloadRepository.save(any(DevicePosturePayload.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(profileRepository.save(any(DeviceTrustProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(snapshotRepository.save(any(DeviceSystemSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(installedApplicationRepository.save(any(DeviceInstalledApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(runRepository.save(any(PostureEvaluationRun.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(matchRepository.save(any(PostureEvaluationMatch.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(scoreEventRepository.save(any(DeviceTrustScoreEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(remediationRepository.save(any(PostureEvaluationRemediation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(decisionRepository.save(any(DeviceDecisionResponse.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
    }

    @Test
    void ingestAndEvaluate_payloadMissingAfterIngestReturns404() {
        PosturePayloadIngestRequest request = requestWithPayloadJson(objectMapper.createObjectNode().put("os_type", "ANDROID"));
        when(ingestService.ingest("tenant-a", request)).thenReturn(100L);
        when(payloadRepository.findById(100L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.ingestAndEvaluate("tenant-a", request)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void ingestAndEvaluate_invalidPayloadJsonMarksFailedAndReturns400() {
        PosturePayloadIngestRequest request = requestWithPayloadJson(objectMapper.createObjectNode().put("os_type", "ANDROID"));
        DevicePosturePayload payload = payload(101L, "{invalid-json}");
        when(ingestService.ingest("tenant-a", request)).thenReturn(101L);
        when(payloadRepository.findById(101L)).thenReturn(Optional.of(payload));
        when(runRepository.findOneByPayloadId(101L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.ingestAndEvaluate("tenant-a", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        ArgumentCaptor<DevicePosturePayload> captor = ArgumentCaptor.forClass(DevicePosturePayload.class);
        verify(payloadRepository, atLeastOnce()).save(captor.capture());
        DevicePosturePayload last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("FAILED", last.getProcessStatus());
        assertEquals("Invalid payload_json", last.getProcessError());
    }

    @Test
    void ingestAndEvaluate_happyPathExecutesRulesLifecycleScoringAndRemediation() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_type", "ANDROID");
        root.put("os_name", "ANDROID");
        root.put("os_version", "14.2");
        root.put("device_type", "PHONE");
        root.put("time_zone", "UTC");
        root.put("root_detected", true);
        root.put("capture_time", "2026-03-12T10:00:00Z");
        ArrayNode apps = root.putArray("installed_apps");
        apps.addObject()
                .put("app_name", "Bad App")
                .put("app_os_type", "ANDROID")
                .put("package_id", "com.bad.app")
                .put("app_version", "1.0")
                .put("status", "ACTIVE");
        apps.addObject()
                .put("app_name", "Bad App")
                .put("app_os_type", "ANDROID")
                .put("package_id", "com.bad.app")
                .put("app_version", "1.0")
                .put("status", "ACTIVE");
        apps.addObject()
                .put("app_name", "Ignored")
                .put("app_os_type", "PLAN9")
                .put("status", "UNKNOWN");

        PosturePayloadIngestRequest request = requestWithPayloadJson(root);
        DevicePosturePayload payload = payload(102L, root.toString());
        when(ingestService.ingest("tenant-a", request)).thenReturn(102L);
        when(payloadRepository.findById(102L)).thenReturn(Optional.of(payload));

        PostureEvaluationRun existingRun = new PostureEvaluationRun();
        existingRun.setId(77L);
        existingRun.setCreatedAt(OffsetDateTime.now().minusDays(2));
        existingRun.setCreatedBy("seed");
        when(runRepository.findOneByPayloadId(102L)).thenReturn(Optional.of(existingRun));

        when(profileRepository.findActiveByTenantAndDevice("tenant-a", "dev-01")).thenReturn(Optional.empty());
        when(profileRepository.save(any(DeviceTrustProfile.class))).thenAnswer(invocation -> {
            DeviceTrustProfile profile = invocation.getArgument(0);
            if (profile.getId() == null) {
                profile.setId(20L);
            }
            return profile;
        });

        when(jdbc.queryForObject(contains("device_system_snapshot"), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("device_installed_application"), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        when(snapshotRepository.save(any(DeviceSystemSnapshot.class))).thenAnswer(invocation -> {
            DeviceSystemSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(30L);
            return snapshot;
        });
        AtomicLong appId = new AtomicLong(40L);
        when(installedApplicationRepository.save(any(DeviceInstalledApplication.class))).thenAnswer(invocation -> {
            DeviceInstalledApplication app = invocation.getArgument(0);
            app.setId(appId.getAndIncrement());
            return app;
        });

        OsReleaseLifecycleMaster lifecycle = new OsReleaseLifecycleMaster();
        lifecycle.setId(301L);
        lifecycle.setDeleted(false);
        lifecycle.setStatus("ACTIVE");
        lifecycle.setOsType("ANDROID");
        lifecycle.setCycle("14");
        lifecycle.setEolOn(LocalDate.now().minusDays(1));
        when(osLifecycleRepository.findAll()).thenReturn(List.of(lifecycle));

        SystemInformationRule rule = new SystemInformationRule();
        rule.setId(101L);
        rule.setDeleted(false);
        rule.setStatus("ACTIVE");
        rule.setRuleCode("R-ROOT");
        rule.setRuleTag("TAG-ROOT");
        rule.setComplianceAction("QUARANTINE");
        rule.setRiskScoreDelta((short) -12);
        rule.setSeverity((short) 3);
        rule.setOsType("ANDROID");
        rule.setPriority(1);
        rule.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        when(systemRuleRepository.findAll()).thenReturn(List.of(rule));

        SystemInformationRuleCondition condition = new SystemInformationRuleCondition();
        condition.setId(201L);
        condition.setSystemInformationRuleId(101L);
        condition.setDeleted(false);
        condition.setStatus("ACTIVE");
        condition.setConditionGroup((short) 1);
        condition.setFieldName("root_detected");
        condition.setOperator("EQ");
        condition.setValueBoolean(true);
        when(conditionRepository.findAll()).thenReturn(List.of(condition));

        RejectApplication reject = new RejectApplication();
        reject.setId(202L);
        reject.setDeleted(false);
        reject.setStatus("ACTIVE");
        reject.setAppOsType("ANDROID");
        reject.setAppName("Bad App");
        reject.setPackageId("com.bad.app");
        reject.setPolicyTag("BAD_APP_TAG");
        reject.setMinAllowedVersion("2.0");
        reject.setSeverity((short) 2);
        reject.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        when(rejectApplicationRepository.findAll()).thenReturn(List.of(reject));

        TrustScorePolicy p1 = trustPolicy(203L, "SYSTEM_RULE", "R-ROOT", (short) 3, "QUARANTINE", (short) -20, 1.5);
        TrustScorePolicy p2 = trustPolicy(204L, "REJECT_APPLICATION", "com.bad.app", (short) 2, "BLOCK", (short) -10, 1.0);
        TrustScorePolicy p3 = trustPolicy(205L, "POSTURE_SIGNAL", "OS_EOL", null, null, (short) -5, 1.0);
        when(trustScorePolicyRepository.findAll()).thenReturn(List.of(p1, p2, p3));
        when(trustScoreDecisionPolicyRepository.findActivePolicyForScore(anyString(), anyInt(), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        AtomicLong matchId = new AtomicLong(500L);
        when(matchRepository.save(any(PostureEvaluationMatch.class))).thenAnswer(invocation -> {
            PostureEvaluationMatch match = invocation.getArgument(0);
            match.setId(matchId.getAndIncrement());
            return match;
        });

        AtomicLong eventId = new AtomicLong(600L);
        when(scoreEventRepository.save(any(DeviceTrustScoreEvent.class))).thenAnswer(invocation -> {
            DeviceTrustScoreEvent event = invocation.getArgument(0);
            event.setId(eventId.getAndIncrement());
            return event;
        });

        RuleRemediationMapping m1 = mapping(900L, "SYSTEM_RULE", 101L, null, null, null, 901L, "ENFORCE", (short) 1);
        RuleRemediationMapping m2 = mapping(901L, "TRUST_POLICY", null, null, 205L, null, 902L, "ADVISORY", (short) 2);
        RuleRemediationMapping m3 = mapping(902L, "DECISION", null, null, null, "QUARANTINE", 903L, "ADVISORY", (short) 3);
        when(ruleRemediationMappingRepository.findAll()).thenReturn(List.of(m1, m2, m3));

        RemediationRule r1 = remediationRule(901L, "REM-901");
        RemediationRule r2 = remediationRule(902L, "REM-902");
        RemediationRule r3 = remediationRule(903L, "REM-903");
        when(remediationRuleRepository.findAll()).thenReturn(List.of(r1, r2, r3));

        AtomicLong remediationId = new AtomicLong(700L);
        when(remediationRepository.save(any(PostureEvaluationRemediation.class))).thenAnswer(invocation -> {
            PostureEvaluationRemediation remediation = invocation.getArgument(0);
            remediation.setId(remediationId.getAndIncrement());
            return remediation;
        });

        when(decisionRepository.save(any(DeviceDecisionResponse.class))).thenAnswer(invocation -> {
            DeviceDecisionResponse response = invocation.getArgument(0);
            response.setId(800L);
            return response;
        });

        PosturePayloadIngestResponse response = service.ingestAndEvaluate("tenant-a", request);

        assertNotNull(response);
        assertEquals(102L, response.getPayloadId());
        assertEquals("EVALUATED", response.getStatus());
        assertEquals(77L, response.getEvaluationRunId());
        assertEquals(800L, response.getDecisionResponseId());
        assertEquals("QUARANTINE", response.getDecisionAction());
        assertEquals((short) 55, response.getTrustScore());
        assertTrue(response.isRemediationRequired());
        assertEquals(3, response.getRemediation().size());
        verify(jdbc, atLeastOnce()).update(contains("DELETE FROM posture_evaluation_match"), any(MapSqlParameterSource.class));
        verify(jdbc, atLeastOnce()).update(contains("DELETE FROM posture_evaluation_remediation"), any(MapSqlParameterSource.class));
    }

    @Test
    void privateEvaluateCondition_supportsEqInAndRegexGuards() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("custom", "alpha-01");
        ParsedPosture parsed = parsedPosture(root);

        SystemInformationRuleCondition eq = new SystemInformationRuleCondition();
        eq.setFieldName("api_level");
        eq.setOperator("EQ");
        eq.setValueNumeric(33.0);
        assertEquals(true, invokePrivate("evaluateCondition",
                new Class[]{SystemInformationRuleCondition.class, ParsedPosture.class},
                eq, parsed));

        SystemInformationRuleCondition in = new SystemInformationRuleCondition();
        in.setFieldName("os_type");
        in.setOperator("IN");
        in.setValueJson("[\"ANDROID\", \"IOS\"]");
        assertEquals(true, invokePrivate("evaluateCondition",
                new Class[]{SystemInformationRuleCondition.class, ParsedPosture.class},
                in, parsed));

        SystemInformationRuleCondition badRegex = new SystemInformationRuleCondition();
        badRegex.setFieldName("custom");
        badRegex.setOperator("REGEX");
        badRegex.setValueText("[unclosed");
        assertEquals(false, invokePrivate("evaluateCondition",
                new Class[]{SystemInformationRuleCondition.class, ParsedPosture.class},
                badRegex, parsed));
    }

    @Test
    void privateHelperMethods_coverVersionDecisionAndNormalizationBranches() {
        assertEquals(1, ((Integer) invokePrivate("compareVersion",
                new Class[]{String.class, String.class}, "1.10", "1.2")).intValue());
        assertEquals(0, ((Integer) invokePrivate("compareVersion",
                new Class[]{String.class, String.class}, "1.0.0", "1")).intValue());
        assertEquals("BLOCK", invokePrivate("defaultDecisionForScore",
                new Class[]{short.class}, (short) 30));
        assertEquals("TRUSTED", invokePrivate("scoreBandFor",
                new Class[]{short.class}, (short) 95));
        assertEquals((short) 0, ((Short) invokePrivate("clampScore",
                new Class[]{int.class}, -20)).shortValue());
        assertEquals((short) 100, ((Short) invokePrivate("clampScore",
                new Class[]{int.class}, 150)).shortValue());
        assertEquals("QUARANTINE", invokePrivate("normalizeDecision",
                new Class[]{String.class}, " quarantine "));
        assertNull(invokePrivate("normalizeDecision",
                new Class[]{String.class}, "unsupported"));
        assertEquals("PHONE", invokePrivate("validDeviceType",
                new Class[]{String.class}, "phone"));
        assertNull(invokePrivate("validDeviceType",
                new Class[]{String.class}, "watch"));
    }

    @Test
    void privateParsePayloadJson_rejectsNonObjectJson() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                invokePrivate("parsePayloadJson", new Class[]{String.class}, "[]")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void privateParseOffsetDateTime_invalidInputReturnsEmpty() {
        Optional<?> result = invokePrivate("parseOffsetDateTime", new Class[]{String.class}, "not-a-date");
        assertTrue(result.isEmpty());
    }

    private PosturePayloadIngestRequest requestWithPayloadJson(JsonNode payloadJson) {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-01");
        request.setAgentId("agent-01");
        request.setPayloadVersion("1.0");
        request.setPayloadHash("hash");
        request.setPayloadJson(payloadJson);
        return request;
    }

    private DevicePosturePayload payload(Long id, String payloadJson) {
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(id);
        payload.setTenantId("tenant-a");
        payload.setDeviceExternalId("dev-01");
        payload.setAgentId("agent-01");
        payload.setPayloadJson(payloadJson);
        payload.setProcessStatus("RECEIVED");
        return payload;
    }

    private TrustScorePolicy trustPolicy(Long id,
                                         String sourceType,
                                         String signalKey,
                                         Short severity,
                                         String complianceAction,
                                         Short scoreDelta,
                                         Double weight) {
        TrustScorePolicy policy = new TrustScorePolicy();
        policy.setId(id);
        policy.setDeleted(false);
        policy.setStatus("ACTIVE");
        policy.setSourceType(sourceType);
        policy.setSignalKey(signalKey);
        policy.setSeverity(severity);
        policy.setComplianceAction(complianceAction);
        policy.setScoreDelta(scoreDelta);
        policy.setWeight(weight);
        policy.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return policy;
    }

    private RuleRemediationMapping mapping(Long id,
                                           String sourceType,
                                           Long systemRuleId,
                                           Long rejectAppId,
                                           Long trustPolicyId,
                                           String decisionAction,
                                           Long remediationRuleId,
                                           String enforceMode,
                                           Short rankOrder) {
        RuleRemediationMapping mapping = new RuleRemediationMapping();
        mapping.setId(id);
        mapping.setDeleted(false);
        mapping.setStatus("ACTIVE");
        mapping.setSourceType(sourceType);
        mapping.setSystemInformationRuleId(systemRuleId);
        mapping.setRejectApplicationListId(rejectAppId);
        mapping.setTrustScorePolicyId(trustPolicyId);
        mapping.setDecisionAction(decisionAction);
        mapping.setRemediationRuleId(remediationRuleId);
        mapping.setEnforceMode(enforceMode);
        mapping.setRankOrder(rankOrder);
        mapping.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return mapping;
    }

    private RemediationRule remediationRule(Long id, String code) {
        RemediationRule rule = new RemediationRule();
        rule.setId(id);
        rule.setDeleted(false);
        rule.setStatus("ACTIVE");
        rule.setRemediationCode(code);
        rule.setTitle("Title " + code);
        rule.setDescription("Description " + code);
        rule.setRemediationType("SCRIPT");
        rule.setInstructionJson("{\"cmd\":\"echo ok\"}");
        rule.setOsType("ANDROID");
        rule.setDeviceType("PHONE");
        rule.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        return rule;
    }

    private ParsedPosture parsedPosture(ObjectNode root) {
        return new ParsedPosture(
                "tenant-a",
                "dev-01",
                "agent-01",
                "ANDROID",
                "ANDROID",
                "14.2",
                "14",
                "PHONE",
                "UTC",
                "kernel-1",
                33,
                "build-1",
                "ACME",
                true,
                false,
                false,
                OffsetDateTime.now(),
                root,
                objectMapper.createArrayNode()
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = AgentPostureWorkflowService.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (T) method.invoke(service, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
