package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.domain.DeviceSystemSnapshot;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.domain.PostureEvaluationRemediation;
import com.e24online.mdm.domain.PostureEvaluationRun;
import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.records.IngestionResult;
import com.e24online.mdm.service.evaluation.EvaluationEngineService;
import com.e24online.mdm.service.messaging.PostureEvaluationPublisher;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.RemediationStatusTransition;
import com.e24online.mdm.records.posture.evaluation.SavedRemediation;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.DeviceSystemSnapshotRepository;
import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.DeviceTrustScoreEventRepository;
import com.e24online.mdm.repository.PostureEvaluationMatchRepository;
import com.e24online.mdm.repository.PostureEvaluationRunRepository;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import com.e24online.mdm.web.dto.RemediationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowOrchestrationServiceTest {

    @Mock
    private PostureIngestionService ingestionService;

    @Mock
    private DeviceStateService stateService;

    @Mock
    private EvaluationEngineService evaluationService;

    @Mock
    private RemediationService remediationService;

    @Mock
    private DevicePosturePayloadRepository payloadRepository;

    @Mock
    private DeviceTrustProfileRepository profileRepository;

    @Mock
    private DeviceSystemSnapshotRepository snapshotRepository;

    @Mock
    private PostureEvaluationRunRepository runRepository;

    @Mock
    private PostureEvaluationMatchRepository matchRepository;

    @Mock
    private DeviceTrustScoreEventRepository scoreEventRepository;

    @Mock
    private DeviceDecisionResponseRepository decisionRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private PostureEvaluationPublisher posturePublisher;
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private PayloadFailureService payloadFailureService;

    private ObjectMapper objectMapper;
    private WorkflowOrchestrationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new WorkflowOrchestrationService(
                ingestionService,
                stateService,
                evaluationService,
                remediationService,
                payloadRepository,
                profileRepository,
                snapshotRepository,
                runRepository,
                matchRepository,
                scoreEventRepository,
                decisionRepository,
                jdbc,
                reactor.core.scheduler.Schedulers.immediate(),
                objectMapper,
                posturePublisher,
                auditEventService,
                payloadFailureService
        );
    }

    @Test
    void evaluateExistingPayload_successfulWorkflow_returnsResponse() {
        DevicePosturePayload payload = payload(50L, "{\"os_type\":\"WINDOWS\"}");
        when(payloadRepository.findByIdAndTenant(50L, "tenant-a")).thenReturn(Optional.of(payload));
        when(runRepository.findOneByPayloadId(50L)).thenReturn(Optional.empty());
        when(payloadRepository.save(any(DevicePosturePayload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ParsedPosture parsed = parsedPosture();
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(60L);
        DeviceSystemSnapshot snapshot = new DeviceSystemSnapshot();
        snapshot.setId(70L);
        DeviceInstalledApplication app = new DeviceInstalledApplication();
        app.setId(80L);

        when(stateService.parsePosture(any(), anyString(), anyString(), anyString(), any(OffsetDateTime.class))).thenReturn(parsed);
        when(stateService.upsertTrustProfile(anyString(), any(ParsedPosture.class), any(OffsetDateTime.class))).thenReturn(profile);
        when(stateService.saveSnapshot(any(Long.class), any(DeviceTrustProfile.class), any(ParsedPosture.class), any(OffsetDateTime.class)))
                .thenReturn(snapshot);
        when(stateService.saveInstalledApps(any(Long.class), any(DeviceTrustProfile.class), any(ParsedPosture.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(app));
        when(stateService.resolveLifecycle(any(ParsedPosture.class), any())).thenReturn(new LifecycleResolution(9L, "SUPPORTED", "OS_SUPPORTED"));
        when(profileRepository.save(profile)).thenReturn(profile);
        when(snapshotRepository.save(snapshot)).thenReturn(snapshot);

        EvaluationComputation computed = new EvaluationComputation(
                (short) 80,
                (short) 70,
                (short) -10,
                1,
                0,
                "NOTIFY",
                "risk observed",
                true,
                1000L,
                List.of(new MatchDraft("SYSTEM_RULE", 10L, null, null, null, "SUPPORTED", null, (short) 3, "NOTIFY", (short) -10, "{}")),
                List.of(new ScoreSignal("SYSTEM_RULE", 10L, null, 10L, null, null, "SUPPORTED", (short) -10, "rule"))
        );
        when(evaluationService.computeEvaluation(any(), any(), any(), any(), any())).thenReturn(computed);

        AtomicLong runId = new AtomicLong(100L);
        when(runRepository.save(any(PostureEvaluationRun.class))).thenAnswer(invocation -> {
            PostureEvaluationRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(runId.getAndIncrement());
            }
            return run;
        });
        when(remediationService.saveRemediation(any(), any(), any(), any())).thenReturn(List.of(
                new SavedRemediation(remediationRow(700L), remediationRule(710L), "AUTO")
        ));
        when(remediationService.reconcilePriorOpenRemediations(any(), any(), any(), any())).thenReturn(List.of(
                new RemediationStatusTransition(701L, 95L, 710L, "MATCH", "SYSTEM_RULE", "USER_ACKNOWLEDGED", "STILL_OPEN", null)
        ));
        when(remediationService.buildDecisionPayload(any(), any())).thenReturn("{\"decision\":\"NOTIFY\"}");

        when(decisionRepository.save(any(DeviceDecisionResponse.class))).thenAnswer(invocation -> {
            DeviceDecisionResponse row = invocation.getArgument(0);
            row.setId(900L);
            return row;
        });

        PosturePayloadIngestResponse expectedResponse = new PosturePayloadIngestResponse(
                50L,
                "EVALUATED",
                100L,
                900L,
                "NOTIFY",
                (short) 70,
                "risk observed",
                true,
                List.of(new RemediationSummary(700L, 710L, "R-710", "Title", "Desc", "SCRIPT", "AUTO", "{\"cmd\":\"echo\"}", "PROPOSED"))
        );
        when(remediationService.buildResponse(any(), any(), any(), any())).thenReturn(expectedResponse);

        PosturePayloadIngestResponse response = service.evaluateExistingPayload("tenant-a", 50L);

        assertNotNull(response);
        assertEquals(50L, response.getPayloadId());
        assertEquals(100L, response.getEvaluationRunId());
        assertEquals(900L, response.getDecisionResponseId());
        verify(jdbc, atLeastOnce()).update(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class));
        verify(remediationService).reconcilePriorOpenRemediations(any(), any(), any(), any());
        verify(auditEventService).recordBestEffort(
                eq("REMEDIATION"),
                eq("REMEDIATION_STATUS_CHANGED"),
                eq("RESCAN_VERIFY"),
                eq("tenant-a"),
                eq("rule-engine"),
                eq("POSTURE_EVALUATION_REMEDIATION"),
                eq("701"),
                eq("SUCCESS"),
                any()
        );
    }

    @Test
    void evaluateExistingPayload_invalidPayloadId_rejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.evaluateExistingPayload("tenant-a", 0L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void evaluateExistingPayload_payloadMissing_returns404() {
        when(payloadRepository.findByIdAndTenant(99L, "tenant-a")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.evaluateExistingPayload("tenant-a", 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void evaluateExistingPayload_invalidJson_marksPayloadFailed() {
        DevicePosturePayload payload = payload(88L, "{invalid");
        when(payloadRepository.findByIdAndTenant(88L, "tenant-a")).thenReturn(Optional.of(payload));
        when(runRepository.findOneByPayloadId(88L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.evaluateExistingPayload("tenant-a", 88L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(payloadFailureService).markPayloadFailed(payload, "Invalid payload_json", 900);
    }

    @Test
    void evaluateExistingPayload_dataIntegrityFailure_marksPayloadFailedViaNewTransactionService() {
        DevicePosturePayload payload = payload(89L, "{\"os_type\":\"WINDOWS\",\"os_name\":\"WINDOWS 11\"}");
        when(payloadRepository.findByIdAndTenant(89L, "tenant-a")).thenReturn(Optional.of(payload));
        when(runRepository.findOneByPayloadId(89L)).thenReturn(Optional.empty());
        when(stateService.parsePosture(any(), anyString(), anyString(), anyString(), any(OffsetDateTime.class)))
                .thenReturn(parsedPosture());
        when(stateService.upsertTrustProfile(anyString(), any(ParsedPosture.class), any(OffsetDateTime.class)))
                .thenThrow(new DataIntegrityViolationException("constraint failure"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.evaluateExistingPayload("tenant-a", 89L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(payloadFailureService).markPayloadFailed(payload, "constraint failure", 900);
    }

    @Test
    void ingestAndEvaluate_payloadMissingAfterIngest_returns404() {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-1");
        request.setAgentId("agent-1");
        request.setPayloadVersion("v1");
        request.setPayloadJson(objectMapper.createObjectNode().put("os_type", "WINDOWS"));

        when(ingestionService.ingest("tenant-a", request)).thenReturn(123L);
        when(payloadRepository.findById(123L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.ingestAndEvaluate("tenant-a", request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void ingestAndQueue_claimsPayloadAndPublishesMessage() {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-1");
        request.setAgentId("agent-1");
        request.setPayloadVersion("v1");
        request.setPayloadHash("hash-1");
        request.setPayloadJson(objectMapper.createObjectNode().put("os_type", "WINDOWS"));

        DevicePosturePayload payload = payload(120L, "{\"os_type\":\"WINDOWS\"}");
        payload.setPayloadHash("hash-1");
        payload.setIdempotencyKey("idempo-1");

        when(ingestionService.ingestWithResolution("tenant-a", request))
                .thenReturn(new IngestionResult(payload, true));
        when(payloadRepository.claimPayloadForQueue(120L)).thenReturn(1);

        PosturePayloadIngestResponse response = service.ingestAndQueue("tenant-a", request);

        assertNotNull(response);
        assertEquals(120L, response.getPayloadId());
        assertEquals("QUEUED", response.getStatus());
        assertNull(response.isRemediationRequired());
        assertNull(response.getRemediation());
        verify(posturePublisher).publish(any());
    }

    @Test
    void ingestAndQueue_doesNotPublishWhenAlreadyQueued() {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-1");
        request.setAgentId("agent-1");
        request.setPayloadVersion("v1");
        request.setPayloadHash("hash-1");
        request.setPayloadJson(objectMapper.createObjectNode().put("os_type", "WINDOWS"));

        DevicePosturePayload payload = payload(121L, "{\"os_type\":\"WINDOWS\"}");
        payload.setPayloadHash("hash-1");
        payload.setIdempotencyKey("idempo-1");
        payload.setProcessStatus("QUEUED");

        when(ingestionService.ingestWithResolution("tenant-a", request))
                .thenReturn(new IngestionResult(payload, false));

        PosturePayloadIngestResponse response = service.ingestAndQueue("tenant-a", request);

        assertNotNull(response);
        assertEquals(121L, response.getPayloadId());
        assertEquals("QUEUED", response.getStatus());
        assertNull(response.isRemediationRequired());
        assertNull(response.getRemediation());
        verify(posturePublisher, never()).publish(any());
    }

    @Test
    void getPayloadResult_forFailedPayload_returnsFailureReasonInDecisionReason() {
        DevicePosturePayload failed = payload(140L, "{\"os_type\":\"WINDOWS\"}");
        failed.setProcessStatus("FAILED");
        failed.setProcessError("invalid payload_json");

        when(payloadRepository.findByIdAndTenant(140L, "tenant-a")).thenReturn(Optional.of(failed));

        PosturePayloadIngestResponse response = service.getPayloadResult("tenant-a", 140L);

        assertNotNull(response);
        assertEquals(140L, response.getPayloadId());
        assertEquals("FAILED", response.getStatus());
        assertEquals("invalid payload_json", response.getDecisionReason());
    }

    @Test
    void getPayloadResult_forEvaluatedPayload_marksDecisionDelivered() {
        DevicePosturePayload evaluated = payload(141L, "{\"os_type\":\"WINDOWS\"}");
        evaluated.setProcessStatus("EVALUATED");

        PostureEvaluationRun run = new PostureEvaluationRun();
        run.setId(501L);
        run.setDecisionAction("ALLOW");
        run.setTrustScoreAfter((short) 90);
        run.setDecisionReason("ok");
        run.setRemediationRequired(true);
        run.setResponsePayload("""
                {
                  "evaluation_run_id": 501,
                  "decision_action": "ALLOW",
                  "trust_score": 90,
                  "decision_reason": "ok",
                  "remediation_required": true,
                  "remediation": [
                    {
                      "evaluation_remediation_id": 9001,
                      "remediation_rule_id": 9002,
                      "remediation_code": "REM-1",
                      "title": "Update OS",
                      "description": "Apply supported release",
                      "remediation_type": "OS_UPDATE",
                      "enforce_mode": "ADVISORY",
                      "instruction": "step-1",
                      "status": "DELIVERED"
                    }
                  ]
                }
                """);

        DeviceDecisionResponse decision = new DeviceDecisionResponse();
        decision.setId(601L);
        decision.setPostureEvaluationRunId(501L);
        decision.setDeliveryStatus("PENDING");

        when(payloadRepository.findByIdAndTenant(141L, "tenant-a")).thenReturn(Optional.of(evaluated));
        when(runRepository.findOneByPayloadId(141L)).thenReturn(Optional.of(run));
        when(decisionRepository.findByRunIdAndTenant(501L, "tenant-a")).thenReturn(Optional.of(decision));
        when(decisionRepository.save(any(DeviceDecisionResponse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PosturePayloadIngestResponse response = service.getPayloadResult("tenant-a", 141L);

        assertNotNull(response);
        assertEquals("EVALUATED", response.getStatus());
        assertEquals(501L, response.getEvaluationRunId());
        assertEquals(601L, response.getDecisionResponseId());
        assertEquals("ALLOW", response.getDecisionAction());
        assertNotNull(response.getRemediation());
        assertEquals(1, response.getRemediation().size());
        assertEquals("REM-1", response.getRemediation().getFirst().getRemediationCode());
        assertEquals("DELIVERED", decision.getDeliveryStatus());
        verify(remediationService).markDelivered(501L);
    }

    @Test
    void queueExistingPayload_failedPayloadClaimsAndPublishesMessage() {
        DevicePosturePayload payload = payload(142L, "{\"os_type\":\"WINDOWS\"}");
        payload.setPayloadHash("hash-2");
        payload.setIdempotencyKey("idempo-2");
        payload.setProcessStatus("FAILED");

        when(payloadRepository.findByIdAndTenant(142L, "tenant-a")).thenReturn(Optional.of(payload));
        when(payloadRepository.claimPayloadForQueue(142L)).thenReturn(1);

        PosturePayloadIngestResponse response = service.queueExistingPayload("tenant-a", 142L);

        assertNotNull(response);
        assertEquals(142L, response.getPayloadId());
        assertEquals("QUEUED", response.getStatus());
        verify(posturePublisher).publish(any());
    }

    private DevicePosturePayload payload(Long id, String json) {
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(id);
        payload.setTenantId("tenant-a");
        payload.setDeviceExternalId("dev-1");
        payload.setAgentId("agent-1");
        payload.setPayloadJson(json);
        payload.setProcessStatus("RECEIVED");
        return payload;
    }

    private ParsedPosture parsedPosture() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_type", "WINDOWS");
        root.put("os_name", "WINDOWS 11");
        root.put("os_version", "11");
        return new ParsedPosture(
                "tenant-a",
                "dev-1",
                "agent-1",
                "WINDOWS",
                "WINDOWS 11",
                "11",
                "11",
                "LAPTOP",
                "UTC",
                "1.0",
                33,
                "19045",
                "Acme",
                false,
                false,
                false,
                OffsetDateTime.now(),
                root,
                objectMapper.createArrayNode()
        );
    }

    private PostureEvaluationRemediation remediationRow(Long id) {
        PostureEvaluationRemediation row = new PostureEvaluationRemediation();
        row.setId(id);
        row.setRemediationStatus("PROPOSED");
        row.setInstructionOverride("{\"cmd\":\"echo\"}");
        return row;
    }

    private RemediationRule remediationRule(Long id) {
        RemediationRule rule = new RemediationRule();
        rule.setId(id);
        rule.setRemediationCode("R-" + id);
        rule.setTitle("Title");
        rule.setDescription("Desc");
        rule.setRemediationType("SCRIPT");
        rule.setInstructionJson("{\"cmd\":\"echo\"}");
        return rule;
    }
}
