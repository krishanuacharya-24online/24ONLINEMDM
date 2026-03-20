package com.e24online.mdm.web;

import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.domain.PostureEvaluationRemediation;
import com.e24online.mdm.domain.PostureEvaluationRun;
import com.e24online.mdm.records.RemediationStatusUpdateRequest;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.PostureEvaluationMatchRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.repository.PostureEvaluationRunRepository;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.WorkflowOrchestrationService;
import com.e24online.mdm.web.dto.CreateEvaluationRunRequest;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationsControllerTest {

    @Mock
    private PostureEvaluationRunRepository runRepository;

    @Mock
    private PostureEvaluationMatchRepository matchRepository;

    @Mock
    private PostureEvaluationRemediationRepository remediationRepository;

    @Mock
    private DeviceDecisionResponseRepository decisionResponseRepository;

    @Mock
    private DevicePosturePayloadRepository payloadRepository;

    @Mock
    private WorkflowOrchestrationService workflowService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private EvaluationsController controller;

    @BeforeEach
    void setUp() {
        controller = new EvaluationsController(
                runRepository,
                matchRepository,
                remediationRepository,
                decisionResponseRepository,
                payloadRepository,
                workflowService,
                new BlockingDb(Schedulers.immediate()),
                requestContext
        );
    }

    @Test
    void listRuns_normalizesPageAndSize() {
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findPagedByTenant("tenant-a", "RUNNING", 500, 0))
                .thenReturn(List.of(new PostureEvaluationRun()));

        List<PostureEvaluationRun> runs = controller
                .listRuns("tenant-a", authentication, " RUNNING ", -5, 999)
                .collectList()
                .block();

        assertNotNull(runs);
        assertEquals(1, runs.size());
        verify(runRepository).findPagedByTenant("tenant-a", "RUNNING", 500, 0);
    }

    @Test
    void createRun_returnsExistingWhenNotForced() {
        PostureEvaluationRun existing = runWithId(21L, "COMPLETED", 99L);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(payloadRepository.findByIdAndTenant(99L, "tenant-a")).thenReturn(Optional.of(new DevicePosturePayload()));
        when(runRepository.findOneByPayloadId(99L)).thenReturn(Optional.of(existing));

        CreateEvaluationRunRequest request = new CreateEvaluationRunRequest();
        request.setPayloadId(99L);
        request.setForceRecalculate(false);
        request.setRequestedBy("admin");

        ResponseEntity<PostureEvaluationRun> response = controller
                .createRun("tenant-a", authentication, Mono.just(request))
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(21L, response.getBody().getId());
    }

    @Test
    void createRun_newRunTriggersWorkflowAndReturnsCreated() {
        PostureEvaluationRun saved = runWithId(55L, "IN_PROGRESS", 100L);
        PosturePayloadIngestResponse workflowResponse = new PosturePayloadIngestResponse();
        workflowResponse.setEvaluationRunId(55L);

        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(payloadRepository.findByIdAndTenant(100L, "tenant-a")).thenReturn(Optional.of(new DevicePosturePayload()));
        when(runRepository.findOneByPayloadId(100L)).thenReturn(Optional.empty());
        when(workflowService.evaluateExistingPayloadAsync("tenant-a", 100L)).thenReturn(Mono.just(workflowResponse));
        when(runRepository.findByIdAndTenant(55L, "tenant-a")).thenReturn(Optional.of(saved));

        CreateEvaluationRunRequest request = new CreateEvaluationRunRequest();
        request.setPayloadId(100L);
        request.setForceRecalculate(false);
        request.setRequestedBy("admin");

        ResponseEntity<PostureEvaluationRun> response = controller
                .createRun("tenant-a", authentication, Mono.just(request))
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(55L, response.getBody().getId());
    }

    @Test
    void createRun_invalidPayloadId_returnsBadRequest() {
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        CreateEvaluationRunRequest request = new CreateEvaluationRunRequest();
        request.setPayloadId(0L);
        request.setRequestedBy("admin");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .createRun("tenant-a", authentication, Mono.just(request))
                .block());

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateRunRemediation_invalidStatus_returnsBadRequest() {
        PostureEvaluationRun run = runWithId(33L, "RUNNING", 88L);
        PostureEvaluationRemediation remediation = new PostureEvaluationRemediation();
        remediation.setId(77L);
        remediation.setPostureEvaluationRunId(33L);

        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findByIdAndTenant(33L, "tenant-a")).thenReturn(Optional.of(run));
        when(remediationRepository.findByIdAndRunId(77L, 33L)).thenReturn(Optional.of(remediation));

        RemediationStatusUpdateRequest body =
                new RemediationStatusUpdateRequest("INVALID", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .updateRunRemediation("tenant-a", authentication, 33L, 77L, Mono.just(body))
                .block());

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateRunRemediation_acknowledged_setsCompletedAt() {
        PostureEvaluationRun run = runWithId(33L, "RUNNING", 88L);
        PostureEvaluationRemediation remediation = new PostureEvaluationRemediation();
        remediation.setId(77L);
        remediation.setPostureEvaluationRunId(33L);

        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findByIdAndTenant(33L, "tenant-a")).thenReturn(Optional.of(run));
        when(remediationRepository.findByIdAndRunId(77L, 33L)).thenReturn(Optional.of(remediation));
        when(remediationRepository.save(any(PostureEvaluationRemediation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RemediationStatusUpdateRequest body =
                new RemediationStatusUpdateRequest("ACKED", null);

        PostureEvaluationRemediation response = controller
                .updateRunRemediation("tenant-a", authentication, 33L, 77L, Mono.just(body))
                .block();

        assertNotNull(response);
        assertEquals("USER_ACKNOWLEDGED", response.getRemediationStatus());
        assertNotNull(response.getCompletedAt());
    }

    @Test
    void getRunDecisionResponse_missingResponse_returns404() {
        PostureEvaluationRun run = runWithId(44L, "COMPLETED", 77L);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findByIdAndTenant(44L, "tenant-a")).thenReturn(Optional.of(run));
        when(decisionResponseRepository.findByRunIdAndTenant(44L, "tenant-a")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .getRunDecisionResponse("tenant-a", authentication, 44L)
                .block());

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void cancelRun_terminalStatus_returnsConflict() {
        PostureEvaluationRun run = runWithId(50L, "COMPLETED", 66L);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findByIdAndTenant(50L, "tenant-a")).thenReturn(Optional.of(run));

        ResponseEntity<Void> response = controller.cancelRun("tenant-a", authentication, 50L).block();

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void cancelRun_runningStatus_cancelsAndSavesRun() {
        PostureEvaluationRun run = runWithId(51L, "RUNNING", 67L);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findByIdAndTenant(51L, "tenant-a")).thenReturn(Optional.of(run));
        when(runRepository.save(run)).thenReturn(run);

        ResponseEntity<Void> response = controller.cancelRun("tenant-a", authentication, 51L).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", run.getEvaluationStatus());
        assertEquals("Run cancelled by request", run.getDecisionReason());
        verify(runRepository).save(run);
    }

    @Test
    void retryRun_missingPayloadReference_returnsBadRequest() {
        PostureEvaluationRun run = runWithId(52L, "FAILED", null);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findByIdAndTenant(52L, "tenant-a")).thenReturn(Optional.of(run));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .retryRun("tenant-a", authentication, 52L)
                .block());

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void retryRun_success_returnsOk() {
        PostureEvaluationRun run = runWithId(53L, "FAILED", 200L);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(runRepository.findByIdAndTenant(53L, "tenant-a")).thenReturn(Optional.of(run));
        when(workflowService.evaluateExistingPayloadAsync("tenant-a", 200L))
                .thenReturn(Mono.just(new PosturePayloadIngestResponse()));

        ResponseEntity<Void> response = controller.retryRun("tenant-a", authentication, 53L).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(workflowService).evaluateExistingPayloadAsync(eq("tenant-a"), eq(200L));
    }

    @Test
    void reprocessFailedPayloads_requeuesFailedPayloads() {
        DevicePosturePayload failed = new DevicePosturePayload();
        failed.setId(71L);
        failed.setTenantId("tenant-a");
        failed.setDeviceExternalId("dev-1");
        failed.setProcessStatus("FAILED");

        when(payloadRepository.findPaged("tenant-a", "dev-1", "FAILED", 100, 0))
                .thenReturn(List.of(failed));
        when(workflowService.queueExistingPayload("tenant-a", 71L))
                .thenReturn(new PosturePayloadIngestResponse());

        ResponseEntity<?> response = controller.reprocessFailedPayloads("dev-1", "tenant-a");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(workflowService).queueExistingPayload("tenant-a", 71L);
    }

    private PostureEvaluationRun runWithId(Long id, String status, Long payloadId) {
        PostureEvaluationRun run = new PostureEvaluationRun();
        run.setId(id);
        run.setEvaluationStatus(status);
        run.setDevicePosturePayloadId(payloadId);
        return run;
    }
}
