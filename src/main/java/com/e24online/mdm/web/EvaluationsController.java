package com.e24online.mdm.web;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.PostureEvaluationMatch;
import com.e24online.mdm.domain.PostureEvaluationRemediation;
import com.e24online.mdm.domain.PostureEvaluationRun;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.PostureEvaluationMatchRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.repository.PostureEvaluationRunRepository;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.WorkflowOrchestrationService;
import com.e24online.mdm.web.dto.CreateEvaluationRunRequest;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("${api.version.prefix:v1}/evaluations")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class EvaluationsController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;
    private static final Set<String> CANCELLABLE_STATUSES = Set.of("QUEUED", "IN_PROGRESS", "VALIDATING", "RUNNING");
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "FAILED", "CANCELLED");
    private static final Set<String> VALID_REMEDIATION_STATUSES = Set.of("PENDING", "SENT", "ACKED", "SKIPPED", "FAILED");

    private final PostureEvaluationRunRepository runRepository;
    private final PostureEvaluationMatchRepository matchRepository;
    private final PostureEvaluationRemediationRepository remediationRepository;
    private final DeviceDecisionResponseRepository decisionResponseRepository;
    private final DevicePosturePayloadRepository payloadRepository;
    private final WorkflowOrchestrationService workflowService;
    private final BlockingDb blockingDb;
    private final AuthenticatedRequestContext requestContext;

    public EvaluationsController(PostureEvaluationRunRepository runRepository,
                                 PostureEvaluationMatchRepository matchRepository,
                                 PostureEvaluationRemediationRepository remediationRepository,
                                 DeviceDecisionResponseRepository decisionResponseRepository,
                                 DevicePosturePayloadRepository payloadRepository,
                                 WorkflowOrchestrationService workflowService,
                                 BlockingDb blockingDb,
                                 AuthenticatedRequestContext requestContext) {
        this.runRepository = runRepository;
        this.matchRepository = matchRepository;
        this.remediationRepository = remediationRepository;
        this.decisionResponseRepository = decisionResponseRepository;
        this.payloadRepository = payloadRepository;
        this.workflowService = workflowService;
        this.blockingDb = blockingDb;
        this.requestContext = requestContext;
    }

    @GetMapping("/runs")
    public Flux<PostureEvaluationRun> listRuns(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @RequestParam(name = "evaluation_status", required = false) String evaluationStatus,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        int safeSize = normalizePageSize(size);
        int safePage = normalizePage(page);
        long offset = (long) safePage * safeSize;
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMapMany(normalizedTenantId -> blockingDb.flux(() -> runRepository.findPagedByTenant(
                        normalizedTenantId,
                        normalizeOptionalText(evaluationStatus),
                        safeSize,
                        offset
                )));
    }

    @PostMapping("/runs")
    public Mono<ResponseEntity<PostureEvaluationRun>> createRun(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @Valid @RequestBody Mono<CreateEvaluationRunRequest> request
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(normalizedTenantId -> request.flatMap(req -> {
                    Long payloadId = normalizePayloadId(req.getPayloadId());
                    return blockingDb.mono(() -> {
                        payloadRepository.findByIdAndTenant(payloadId, normalizedTenantId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
                        PostureEvaluationRun existing = runRepository.findOneByPayloadId(payloadId).orElse(null);
                        if (existing != null && !req.isForceRecalculate()) {
                            return new CreateRunResolution(existing, false, HttpStatus.OK);
                        }
                        HttpStatus responseStatus = existing == null ? HttpStatus.CREATED : HttpStatus.OK;
                        return new CreateRunResolution(existing, true, responseStatus);
                    }).flatMap(resolution -> {
                        if (!resolution.evaluate()) {
                            return Mono.just(ResponseEntity.status(resolution.responseStatus()).body(resolution.run()));
                        }
                        return workflowService.evaluateExistingPayloadAsync(normalizedTenantId, payloadId)
                                .flatMap(response -> loadRunById(normalizedTenantId, response.getEvaluationRunId()))
                                .map(run -> ResponseEntity.status(resolution.responseStatus()).body(run));
                    });
                }));
    }

    @GetMapping("/runs/{run_id}")
    public Mono<PostureEvaluationRun> getRun(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("run_id") Long runId
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(normalizedTenantId -> requireRun(normalizedTenantId, runId));
    }

    @GetMapping("/runs/{run_id}/matches")
    public Flux<PostureEvaluationMatch> getRunMatches(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("run_id") Long runId
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMapMany(normalizedTenantId -> requireRun(normalizedTenantId, runId)
                        .flatMapMany(run -> blockingDb.flux(() -> matchRepository.findByRunId(runId))));
    }

    @GetMapping("/runs/{run_id}/remediations")
    public Flux<PostureEvaluationRemediation> getRunRemediations(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("run_id") Long runId
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMapMany(normalizedTenantId -> requireRun(normalizedTenantId, runId)
                        .flatMapMany(run -> blockingDb.flux(() -> remediationRepository.findByRunId(runId))));
    }

    @PatchMapping("/runs/{run_id}/remediations/{remediation_id}")
    public Mono<PostureEvaluationRemediation> updateRunRemediation(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("run_id") Long runId,
            @PathVariable("remediation_id") Long remediationId,
            @Valid @RequestBody Mono<RemediationStatusUpdateRequest> request
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(normalizedTenantId -> requireRun(normalizedTenantId, runId)
                        .flatMap(run -> request.flatMap(body -> blockingDb.mono(() -> {
                            PostureEvaluationRemediation remediation = remediationRepository.findByIdAndRunId(
                                            remediationId,
                                            runId
                                    )
                                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remediation not found"));
                            String normalizedStatus = normalizeRemediationStatus(body.remediationStatus());
                            remediation.setRemediationStatus(normalizedStatus);
                            remediation.setCompletedAt(resolveCompletedAt(normalizedStatus, body.completedAt()));
                            return remediationRepository.save(remediation);
                        }))));
    }

    @GetMapping("/runs/{run_id}/decision-response")
    public Mono<DeviceDecisionResponse> getRunDecisionResponse(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("run_id") Long runId
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(normalizedTenantId -> requireRun(normalizedTenantId, runId)
                        .then(blockingDb.mono(() -> decisionResponseRepository.findByRunIdAndTenant(runId, normalizedTenantId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision response not found")))));
    }

    @PostMapping("/runs/{run_id}/cancel")
    public Mono<ResponseEntity<Void>> cancelRun(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("run_id") Long runId
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(normalizedTenantId -> blockingDb.mono(() -> {
                    PostureEvaluationRun run = runRepository.findByIdAndTenant(runId, normalizedTenantId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));

                    String status = normalizeUpper(run.getEvaluationStatus());
                    if ("CANCELLED".equals(status)) {
                        return ResponseEntity.ok().build();
                    }
                    if (status != null && TERMINAL_STATUSES.contains(status)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }
                    if (status != null && !CANCELLABLE_STATUSES.contains(status)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }

                    OffsetDateTime now = OffsetDateTime.now();
                    run.setEvaluationStatus("CANCELLED");
                    run.setDecisionReason("Run cancelled by request");
                    run.setRespondedAt(now);
                    run.setEvaluatedAt(now);
                    runRepository.save(run);
                    return ResponseEntity.ok().build();
                }));
    }

    @PostMapping("/runs/{run_id}/retry")
    public Mono<ResponseEntity<Void>> retryRun(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("run_id") Long runId
    ) {
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(normalizedTenantId -> requireRun(normalizedTenantId, runId)
                        .flatMap(run -> {
                            Long payloadId = run.getDevicePosturePayloadId();
                            if (payloadId == null) {
                                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Run has no payload reference"));
                            }
                            return workflowService.evaluateExistingPayloadAsync(normalizedTenantId, payloadId);
                        })
                        .thenReturn(ResponseEntity.ok().build()));
    }

    private Mono<PostureEvaluationRun> requireRun(String tenantId, Long runId) {
        return blockingDb.mono(() -> runRepository.findByIdAndTenant(runId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found")));
    }

    private Mono<PostureEvaluationRun> loadRunById(String tenantId, Long runId) {
        if (runId == null || runId <= 0L) {
            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Evaluation run id is missing"));
        }
        return requireRun(tenantId, runId);
    }

    private Long normalizePayloadId(Long payloadId) {
        if (payloadId == null || payloadId <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payloadId must be positive");
        }
        return payloadId;
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRemediationStatus(String remediationStatus) {
        String normalized = normalizeOptionalText(remediationStatus);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remediation_status is required");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!VALID_REMEDIATION_STATUSES.contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid remediation_status");
        }
        return upper;
    }

    private OffsetDateTime resolveCompletedAt(String remediationStatus, OffsetDateTime requestedCompletedAt) {
        if ("ACKED".equals(remediationStatus) || "SKIPPED".equals(remediationStatus) || "FAILED".equals(remediationStatus)) {
            return requestedCompletedAt != null ? requestedCompletedAt : OffsetDateTime.now();
        }
        return null;
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
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

    private record CreateRunResolution(PostureEvaluationRun run, boolean evaluate, HttpStatus responseStatus) {
    }

    public record RemediationStatusUpdateRequest(
            @NotBlank
            @JsonAlias({"remediation_status", "remediationStatus"})
            String remediationStatus,
            @JsonAlias({"completed_at", "completedAt"})
            OffsetDateTime completedAt
    ) {
    }
}
