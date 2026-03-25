package com.e24online.mdm.service;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.records.IngestionResult;
import com.e24online.mdm.service.evaluation.EvaluationEngineService;
import com.e24online.mdm.web.dto.PostureEvaluationMessage;
import com.e24online.mdm.service.messaging.PostureEvaluationPublisher;
import com.e24online.mdm.records.posture.evaluation.*;
import com.e24online.mdm.repository.*;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import com.e24online.mdm.web.dto.RemediationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.e24online.mdm.utils.WorkflowStatusModel.canonicalDeliveryStatus;
import static com.e24online.mdm.utils.WorkflowStatusModel.isAcknowledgedDeliveryStatus;

/**
 * Orchestrates the complete posture evaluation workflow.
 * Coordinates ingestion, device state management, evaluation, and remediation.
 */
@Service
public class WorkflowOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrationService.class);
    private static final int MAX_PROCESS_ERROR_LENGTH = 900;

    private final PostureIngestionService ingestionService;
    private final DeviceStateService stateService;
    private final EvaluationEngineService evaluationService;
    private final RemediationService remediationService;
    private final DevicePosturePayloadRepository payloadRepository;
    private final DeviceTrustProfileRepository profileRepository;
    private final DeviceSystemSnapshotRepository snapshotRepository;
    private final PostureEvaluationRunRepository runRepository;
    private final PostureEvaluationMatchRepository matchRepository;
    private final DeviceTrustScoreEventRepository scoreEventRepository;
    private final DeviceDecisionResponseRepository decisionRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Scheduler jdbcScheduler;
    private final ObjectMapper objectMapper;
    private final PostureEvaluationPublisher posturePublisher;
    private final AuditEventService auditEventService;
    private final PayloadFailureService payloadFailureService;

    public WorkflowOrchestrationService(PostureIngestionService ingestionService,
                                        DeviceStateService stateService,
                                        EvaluationEngineService evaluationService,
                                        RemediationService remediationService,
                                        DevicePosturePayloadRepository payloadRepository,
                                        DeviceTrustProfileRepository profileRepository,
                                        DeviceSystemSnapshotRepository snapshotRepository,
                                        PostureEvaluationRunRepository runRepository,
                                        PostureEvaluationMatchRepository matchRepository,
                                        DeviceTrustScoreEventRepository scoreEventRepository,
                                        DeviceDecisionResponseRepository decisionRepository,
                                        NamedParameterJdbcTemplate jdbc,
                                        Scheduler jdbcScheduler,
                                        ObjectMapper objectMapper,
                                        PostureEvaluationPublisher posturePublisher,
                                        AuditEventService auditEventService,
                                        PayloadFailureService payloadFailureService) {
        this.ingestionService = ingestionService;
        this.stateService = stateService;
        this.evaluationService = evaluationService;
        this.remediationService = remediationService;
        this.payloadRepository = payloadRepository;
        this.profileRepository = profileRepository;
        this.snapshotRepository = snapshotRepository;
        this.runRepository = runRepository;
        this.matchRepository = matchRepository;
        this.scoreEventRepository = scoreEventRepository;
        this.decisionRepository = decisionRepository;
        this.jdbc = jdbc;
        this.jdbcScheduler = jdbcScheduler;
        this.objectMapper = objectMapper;
        this.posturePublisher = posturePublisher;
        this.auditEventService = auditEventService;
        this.payloadFailureService = payloadFailureService;
    }

    /**
     * Async entry point for posture ingestion and evaluation.
     */
    public Mono<PosturePayloadIngestResponse> ingestAndEvaluateAsync(String tenantId, PosturePayloadIngestRequest request) {
        log.debug("Starting posture ingestion and evaluation for tenant: {}", tenantId);
        return Mono.fromCallable(() -> ingestAndEvaluate(tenantId, request))
                .subscribeOn(jdbcScheduler);
    }

    public Mono<PosturePayloadIngestResponse> ingestAndQueueAsync(String tenantId, PosturePayloadIngestRequest request) {
        log.debug("Starting posture ingestion and queue handoff for tenant: {}", tenantId);
        return Mono.fromCallable(() -> ingestAndQueue(tenantId, request))
                .subscribeOn(jdbcScheduler);
    }

    public Mono<PosturePayloadIngestResponse> evaluateExistingPayloadAsync(String tenantId, Long payloadId) {
        log.debug("Starting re-evaluation for tenant: {} payloadId={}", tenantId, payloadId);
        return Mono.fromCallable(() -> evaluateExistingPayload(tenantId, payloadId))
                .subscribeOn(jdbcScheduler);
    }

    public Mono<PosturePayloadIngestResponse> queueExistingPayloadAsync(String tenantId, Long payloadId) {
        log.debug("Starting re-queue for tenant: {} payloadId={}", tenantId, payloadId);
        return Mono.fromCallable(() -> queueExistingPayload(tenantId, payloadId))
                .subscribeOn(jdbcScheduler);
    }

    public Mono<PosturePayloadIngestResponse> getPayloadResultAsync(String tenantId, Long payloadId) {
        return Mono.fromCallable(() -> getPayloadResult(tenantId, payloadId))
                .subscribeOn(jdbcScheduler);
    }

    /**
     * Main workflow: ingest payload, evaluate posture, generate decision.
     */
    @Transactional
    public PosturePayloadIngestResponse ingestAndEvaluate(String tenantId, PosturePayloadIngestRequest request) {
        log.debug("Executing posture workflow for device: {}", request.getDeviceExternalId());

        Long payloadId = ingestionService.ingest(tenantId, request);
        DevicePosturePayload payload = payloadRepository.findById(payloadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found after ingest"));
        return evaluatePayload(tenantId, payload, request.getDeviceExternalId(), request.getAgentId());
    }

    public PosturePayloadIngestResponse ingestAndQueue(String tenantId, PosturePayloadIngestRequest request) {
        IngestionResult ingestion = ingestionService.ingestWithResolution(tenantId, request);
        DevicePosturePayload payload = ingestion.payload();
        if (payload == null || payload.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payload missing after ingest");
        }
        return queuePayload(payload);
    }

    public PosturePayloadIngestResponse queueExistingPayload(String tenantId, Long payloadId) {
        if (payloadId == null || payloadId <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payloadId must be positive");
        }
        DevicePosturePayload payload = payloadRepository.findByIdAndTenant(payloadId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
        return queuePayload(payload);
    }

    @Transactional
    public PosturePayloadIngestResponse evaluateExistingPayload(String tenantId, Long payloadId) {
        if (payloadId == null || payloadId <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payloadId must be positive");
        }
        DevicePosturePayload payload = payloadRepository.findByIdAndTenant(payloadId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
        return evaluatePayload(tenantId, payload, payload.getDeviceExternalId(), payload.getAgentId());
    }

    public PosturePayloadIngestResponse getPayloadResult(String tenantId, Long payloadId) {
        if (payloadId == null || payloadId <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payloadId must be positive");
        }
        DevicePosturePayload payload = payloadRepository.findByIdAndTenant(payloadId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
        return queueResponse(payload);
    }

    private PosturePayloadIngestResponse evaluatePayload(String tenantId,
                                                         DevicePosturePayload payload,
                                                         String deviceExternalId,
                                                         String agentId) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Long payloadId = payload.getId();

            // Step 2: Check for existing run and parse posture
            PostureEvaluationRun existingRun = runRepository.findOneByPayloadId(payloadId).orElse(null);
            JsonNode root = parsePayloadJson(payload.getPayloadJson());
            ParsedPosture parsed = stateService.parsePosture(
                    root,
                    deviceExternalId,
                    agentId,
                    tenantId,
                    payload.getCaptureTime() != null ? payload.getCaptureTime() : now
            );

            // Step 3: Upsert device trust profile
            DeviceTrustProfile profile = stateService.upsertTrustProfile(tenantId, parsed, now);

            // Step 4: Clear existing artifacts if re-evaluating
            if (existingRun != null) {
                clearRunArtifacts(existingRun.getId());
            }
            clearPayloadArtifacts(payloadId);

            // Step 5: Mark payload as validated
            payload.setProcessStatus("VALIDATED");
            payload.setProcessedAt(now);
            payload.setProcessError(null);
            payloadRepository.save(payload);

            // Step 6: Save system snapshot and installed apps
            DeviceSystemSnapshot snapshot = stateService.saveSnapshot(payloadId, profile, parsed, now);
            List<DeviceInstalledApplication> installedApps = stateService.saveInstalledApps(payloadId, profile, parsed, now);

            // Step 7: Resolve and apply OS lifecycle
            LifecycleResolution lifecycle = stateService.resolveLifecycle(parsed, now.toLocalDate());
            stateService.applyLifecycle(profile, snapshot, lifecycle);
            profileRepository.save(profile);
            snapshotRepository.save(snapshot);

            // Step 8: Compute evaluation (rules, scoring, decision)
            EvaluationComputation computed = evaluationService.computeEvaluation(profile, parsed, installedApps, lifecycle, now);

            // Step 9: Save evaluation run
            PostureEvaluationRun run = existingRun != null ? existingRun : new PostureEvaluationRun();
            populateRun(run, payload, profile, lifecycle, computed, now);
            PostureEvaluationRun savedRun = runRepository.save(run);

            // Step 10: Save matches and score events
            List<SavedMatch> savedMatches = saveMatches(savedRun.getId(), computed.matches(), now);
            saveScoreEvents(savedRun.getId(), profile.getId(), computed.scoreSignals(), computed.scoreBefore(), now);

            // Step 11: Save remediation
            List<SavedRemediation> savedRemediation = remediationService.saveRemediation(savedRun, savedMatches, parsed, now);
            List<RemediationStatusTransition> remediationTransitions = remediationService.reconcilePriorOpenRemediations(
                    savedRun,
                    savedMatches,
                    savedRemediation,
                    now
            );
            auditRemediationRescanTransitions(payload, savedRun, remediationTransitions, now);

            // Step 12: Build and save decision response
            String responsePayload = remediationService.buildDecisionPayload(savedRun, savedRemediation);
            savedRun.setResponsePayload(responsePayload);
            savedRun.setRespondedAt(now);
            savedRun = runRepository.save(savedRun);

            DeviceDecisionResponse decisionResponse = createDecisionResponse(savedRun, payload, responsePayload, now);
            DeviceDecisionResponse savedDecision = decisionRepository.save(decisionResponse);

            // Step 13: Update profile with final scores
            updateProfileScores(profile, savedRun, now);

            // Step 14: Mark payload as evaluated
            payload.setProcessStatus("EVALUATED");
            payload.setProcessedAt(now);
            payload.setProcessError(null);
            payloadRepository.save(payload);

            auditEventService.recordBestEffort(
                    "POSTURE",
                    "POSTURE_EVALUATED",
                    "EVALUATE",
                    payload.getTenantId(),
                    "rule-engine",
                    "DEVICE_POSTURE_PAYLOAD",
                    payloadId == null ? null : String.valueOf(payloadId),
                    "SUCCESS",
                    Map.of(
                            "evaluationRunId", savedRun.getId(),
                            "decisionAction", savedRun.getDecisionAction(),
                            "trustScoreAfter", savedRun.getTrustScoreAfter(),
                            "remediationRequired", savedRun.isRemediationRequired()
                    )
            );

            PosturePayloadIngestResponse response = remediationService.buildResponse(payload, savedRun, savedDecision, savedRemediation);
            applyPayloadContractMetadata(response, payload);
            return response;

        } catch (ResponseStatusException ex) {
            java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("payloadId", payload.getId());
            metadata.put("deviceExternalId", payload.getDeviceExternalId());
            metadata.put("reason", ex.getReason());
            auditEventService.recordBestEffort(
                    "POSTURE",
                    "POSTURE_EVALUATED",
                    "EVALUATE",
                    payload.getTenantId(),
                    "rule-engine",
                    "DEVICE_POSTURE_PAYLOAD",
                    payload.getId() == null ? null : String.valueOf(payload.getId()),
                    "FAILURE",
                    metadata
            );
            markPayloadFailed(payload, ex.getReason());
            throw ex;
        } catch (Exception ex) {
            log.error("Posture evaluation failed for payloadId={} tenantId={} deviceExternalId={}",
                    payload.getId(), payload.getTenantId(), payload.getDeviceExternalId(), ex);
            java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("payloadId", payload.getId());
            metadata.put("deviceExternalId", payload.getDeviceExternalId());
            metadata.put("reason", ex.getMessage());
            auditEventService.recordBestEffort(
                    "POSTURE",
                    "POSTURE_EVALUATED",
                    "EVALUATE",
                    payload.getTenantId(),
                    "rule-engine",
                    "DEVICE_POSTURE_PAYLOAD",
                    payload.getId() == null ? null : String.valueOf(payload.getId()),
                    "FAILURE",
                    metadata
            );
            markPayloadFailed(payload, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posture evaluation failed");
        }
    }

    private PostureEvaluationMessage queueMessage(DevicePosturePayload payload) {
        if (payload.getIdempotencyKey() == null || payload.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotency_key is required before publishing queue messages");
        }
        if (payload.getPayloadHash() == null || payload.getPayloadHash().isBlank()) {
            throw new IllegalArgumentException("payload_hash is required before publishing queue messages");
        }

        PostureEvaluationMessage message = new PostureEvaluationMessage();
        message.setSchemaVersion(PostureEvaluationMessage.CURRENT_SCHEMA_VERSION);
        message.setEventId(UUID.randomUUID().toString());
        message.setTenantId(payload.getTenantId());
        message.setPayloadId(payload.getId());
        message.setDeviceExternalId(payload.getDeviceExternalId());
        message.setPayloadHash(payload.getPayloadHash());
        message.setIdempotencyKey(payload.getIdempotencyKey());
        message.setQueuedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return message;
    }

    private PosturePayloadIngestResponse queueResponse(DevicePosturePayload payload) {
        String status = normalizeStatus(payload.getProcessStatus());
        PosturePayloadIngestResponse response = new PosturePayloadIngestResponse();
        response.setPayloadId(payload.getId());
        response.setStatus(status);
        if ("QUEUED".equals(status)) {
            response.suppressQueuedRemediationFields();
        }
        if ("FAILED".equals(status)) {
            response.setDecisionReason(payload.getProcessError());
        }
        applyPayloadContractMetadata(response, payload);

        if ("EVALUATED".equals(status)) {
            PostureEvaluationRun run = runRepository.findOneByPayloadId(payload.getId()).orElse(null);
            if (run != null) {
                response.setEvaluationRunId(run.getId());
                response.setDecisionAction(run.getDecisionAction());
                response.setTrustScore(run.getTrustScoreAfter());
                response.setDecisionReason(run.getDecisionReason());
                response.setRemediationRequired(run.isRemediationRequired());
                response.setRemediation(readRemediationFromResponsePayload(run.getResponsePayload()));

                DeviceDecisionResponse decision = decisionRepository.findByRunIdAndTenant(run.getId(), payload.getTenantId())
                        .orElse(null);
                if (decision != null) {
                    decision = markDecisionDeliveredIfPending(decision);
                    response.setDecisionResponseId(decision.getId());
                }
            }
        }

        return response;
    }

    private List<RemediationSummary> readRemediationFromResponsePayload(String responsePayload) {
        if (responsePayload == null || responsePayload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responsePayload);
            JsonNode remediationNode = root == null ? null : root.get("remediation");
            if (remediationNode == null || !remediationNode.isArray()) {
                return List.of();
            }
            List<RemediationSummary> remediation = new ArrayList<>();
            remediationNode.forEach(node -> {
                if (node == null || !node.isObject()) {
                    return;
                }
                remediation.add(new RemediationSummary(
                        longNodeValue(node.get("evaluation_remediation_id")),
                        longNodeValue(node.get("remediation_rule_id")),
                        textNodeValue(node.get("remediation_code")),
                        textNodeValue(node.get("title")),
                        textNodeValue(node.get("description")),
                        textNodeValue(node.get("remediation_type")),
                        textNodeValue(node.get("enforce_mode")),
                        textNodeValue(node.get("instruction")),
                        textNodeValue(node.get("status"))
                ));
            });
            return remediation;
        } catch (Exception _) {
            return List.of();
        }
    }

    private PosturePayloadIngestResponse queuePayload(DevicePosturePayload payload) {
        String status = normalizeStatus(payload.getProcessStatus());
        if ("EVALUATED".equals(status) || "VALIDATED".equals(status) || "QUEUED".equals(status)) {
            return queueResponse(payload);
        }

        int claimed = payloadRepository.claimPayloadForQueue(payload.getId());
        if (claimed <= 0) {
            DevicePosturePayload latest = payloadRepository.findById(payload.getId()).orElse(payload);
            return queueResponse(latest);
        }

        try {
            posturePublisher.publish(queueMessage(payload));
            payload.setProcessStatus("QUEUED");
            payload.setProcessError(null);
            payload.setProcessedAt(null);
            auditEventService.recordBestEffort(
                    "POSTURE",
                    "POSTURE_EVALUATION_QUEUED",
                    "QUEUE",
                    payload.getTenantId(),
                    "rule-engine",
                    "DEVICE_POSTURE_PAYLOAD",
                    payload.getId() == null ? null : String.valueOf(payload.getId()),
                    "SUCCESS",
                    Map.of(
                            "deviceExternalId", payload.getDeviceExternalId(),
                            "idempotencyKey", payload.getIdempotencyKey(),
                            "payloadHash", payload.getPayloadHash()
                    )
            );
            return queueResponse(payload);
        } catch (Exception ex) {
            log.error("Failed to enqueue posture evaluation payloadId={} tenantId={}",
                    payload.getId(), payload.getTenantId(), ex);
            java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("payloadId", payload.getId());
            metadata.put("deviceExternalId", payload.getDeviceExternalId());
            metadata.put("reason", ex.getMessage());
            auditEventService.recordBestEffort(
                    "POSTURE",
                    "POSTURE_EVALUATION_QUEUED",
                    "QUEUE",
                    payload.getTenantId(),
                    "rule-engine",
                    "DEVICE_POSTURE_PAYLOAD",
                    payload.getId() == null ? null : String.valueOf(payload.getId()),
                    "FAILURE",
                    metadata
            );
            payloadRepository.markPayloadFailed(
                    payload.getId(),
                    truncate("Queue publish failed: " + ex.getMessage(), MAX_PROCESS_ERROR_LENGTH),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to queue posture evaluation");
        }
    }

    private void applyPayloadContractMetadata(PosturePayloadIngestResponse response, DevicePosturePayload payload) {
        if (response == null || payload == null) {
            return;
        }
        response.setSchemaCompatibilityStatus(payload.getSchemaCompatibilityStatus());
        response.setValidationWarnings(readValidationWarnings(payload.getValidationWarnings()));
    }

    private List<String> readValidationWarnings(String warningsJson) {
        if (warningsJson == null || warningsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode warnings = objectMapper.readTree(warningsJson);
            if (warnings == null || !warnings.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            warnings.forEach(node -> {
                if (node != null && node.isTextual()) {
                    values.add(node.asText());
                }
            });
            return values;
        } catch (Exception _) {
            return List.of();
        }
    }

    private Long longNodeValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException _) {
                return null;
            }
        }
        return null;
    }

    private String textNodeValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private JsonNode parsePayloadJson(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload_json is empty");
        }
        try {
            JsonNode parsed = objectMapper.readTree(payloadJson);
            if (parsed == null || !parsed.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload_json must be an object");
            }
            return parsed;
        } catch (Exception _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload_json");
        }
    }

    private void populateRun(PostureEvaluationRun run,
                             DevicePosturePayload payload,
                             DeviceTrustProfile profile,
                             LifecycleResolution lifecycle,
                             EvaluationComputation computed,
                             OffsetDateTime now) {
        run.setDevicePosturePayloadId(payload.getId());
        run.setDeviceTrustProfileId(profile.getId());
        run.setTrustScoreDecisionPolicyId(computed.decisionPolicyId());
        run.setOsReleaseLifecycleMasterId(lifecycle.masterId());
        run.setOsLifecycleState(lifecycle.state());
        run.setEvaluationStatus("COMPLETED");
        run.setTrustScoreBefore(computed.scoreBefore());
        run.setTrustScoreDeltaTotal(computed.scoreDeltaTotal());
        run.setTrustScoreAfter(computed.scoreAfter());
        run.setDecisionAction(computed.decisionAction());
        run.setDecisionReason(computed.decisionReason());
        run.setRemediationRequired(computed.remediationRequired());
        run.setMatchedRuleCount(computed.matchedRuleCount());
        run.setMatchedAppCount(computed.matchedAppCount());
        run.setResponsePayload(null);
        run.setRespondedAt(null);
        run.setEvaluatedAt(now);
        if (run.getCreatedAt() == null) {
            run.setCreatedAt(now);
        }
        if (run.getCreatedBy() == null || run.getCreatedBy().isBlank()) {
            run.setCreatedBy("rule-engine");
        }
    }

    private List<SavedMatch> saveMatches(Long runId, List<MatchDraft> matchDrafts, OffsetDateTime now) {
        if (matchDrafts == null || matchDrafts.isEmpty()) {
            return List.of();
        }

        String insertSql = """
                INSERT INTO posture_evaluation_match (
                    posture_evaluation_run_id,
                    match_source,
                    system_information_rule_id,
                    reject_application_list_id,
                    trust_score_policy_id,
                    os_release_lifecycle_master_id,
                    os_lifecycle_state,
                    device_installed_application_id,
                    matched,
                    severity,
                    compliance_action,
                    score_delta,
                    match_detail,
                    created_at,
                    created_by
                ) VALUES (
                    :runId,
                    :matchSource,
                    :systemRuleId,
                    :rejectApplicationId,
                    :trustScorePolicyId,
                    :osReleaseLifecycleMasterId,
                    :osLifecycleState,
                    :deviceInstalledApplicationId,
                    :matched,
                    :severity,
                    :complianceAction,
                    :scoreDelta,
                    :matchDetail,
                    :createdAt,
                    :createdBy
                )
                """;

        List<MapSqlParameterSource> batchParams = new ArrayList<>();
        for (MatchDraft draft : matchDrafts) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("matchSource", draft.matchSource())
                    .addValue("systemRuleId", draft.systemRuleId())
                    .addValue("rejectApplicationId", draft.rejectApplicationId())
                    .addValue("trustScorePolicyId", draft.trustScorePolicyId())
                    .addValue("osReleaseLifecycleMasterId", draft.osReleaseLifecycleMasterId())
                    .addValue("osLifecycleState", draft.osLifecycleState())
                    .addValue("deviceInstalledApplicationId", draft.deviceInstalledApplicationId())
                    .addValue("matched", true)
                    .addValue("severity", draft.severity())
                    .addValue("complianceAction", draft.complianceAction())
                    .addValue("scoreDelta", draft.scoreDelta())
                    .addValue("matchDetail", draft.matchDetail())
                    .addValue("createdAt", now)
                    .addValue("createdBy", "rule-engine");
            batchParams.add(params);
        }
        jdbc.batchUpdate(insertSql, batchParams.toArray(MapSqlParameterSource[]::new));

        List<PostureEvaluationMatch> persistedRows = matchRepository.findByRunId(runId);
        if (persistedRows == null || persistedRows.isEmpty()) {
            List<SavedMatch> fallback = new ArrayList<>(matchDrafts.size());
            for (MatchDraft draft : matchDrafts) {
                fallback.add(new SavedMatch(buildMatchRow(runId, draft, now), draft));
            }
            return fallback;
        }
        Map<String, ArrayDeque<PostureEvaluationMatch>> persistedByKey = new HashMap<>();
        for (PostureEvaluationMatch row : persistedRows) {
            persistedByKey.computeIfAbsent(matchKey(row), _ -> new ArrayDeque<>()).add(row);
        }

        List<SavedMatch> saved = new ArrayList<>(matchDrafts.size());
        for (MatchDraft draft : matchDrafts) {
            ArrayDeque<PostureEvaluationMatch> bucket = persistedByKey.get(matchKey(draft));
            if (bucket == null || bucket.isEmpty()) {
                saved.add(new SavedMatch(buildMatchRow(runId, draft, now), draft));
                continue;
            }
            saved.add(new SavedMatch(bucket.removeFirst(), draft));
        }
        return saved;
    }

    private void saveScoreEvents(Long runId, Long profileId, List<ScoreSignal> signals, short scoreBefore, OffsetDateTime now) {
        if (signals == null || signals.isEmpty()) {
            return;
        }

        String insertSql = """
                INSERT INTO device_trust_score_event (
                    device_trust_profile_id,
                    posture_evaluation_run_id,
                    event_source,
                    source_record_id,
                    trust_score_policy_id,
                    system_information_rule_id,
                    reject_application_list_id,
                    os_release_lifecycle_master_id,
                    os_lifecycle_state,
                    score_before,
                    score_delta,
                    score_after,
                    event_time,
                    processed_at,
                    processed_by,
                    notes
                ) VALUES (
                    :profileId,
                    :runId,
                    :eventSource,
                    :sourceRecordId,
                    :trustScorePolicyId,
                    :systemInformationRuleId,
                    :rejectApplicationListId,
                    :osReleaseLifecycleMasterId,
                    :osLifecycleState,
                    :scoreBefore,
                    :scoreDelta,
                    :scoreAfter,
                    :eventTime,
                    :processedAt,
                    :processedBy,
                    :notes
                )
                """;

        List<MapSqlParameterSource> batchParams = new ArrayList<>(signals.size());
        short running = scoreBefore;
        for (ScoreSignal signal : signals) {
            short before = running;
            short after = clampScore(before + signal.scoreDelta());
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("profileId", profileId)
                    .addValue("runId", runId)
                    .addValue("eventSource", signal.eventSource())
                    .addValue("sourceRecordId", signal.sourceRecordId())
                    .addValue("trustScorePolicyId", signal.trustScorePolicyId())
                    .addValue("systemInformationRuleId", signal.systemRuleId())
                    .addValue("rejectApplicationListId", signal.rejectApplicationId())
                    .addValue("osReleaseLifecycleMasterId", signal.osReleaseLifecycleMasterId())
                    .addValue("osLifecycleState", signal.osLifecycleState())
                    .addValue("scoreBefore", before)
                    .addValue("scoreDelta", signal.scoreDelta())
                    .addValue("scoreAfter", after)
                    .addValue("eventTime", now)
                    .addValue("processedAt", now)
                    .addValue("processedBy", "rule-engine")
                    .addValue("notes", signal.notes());
            batchParams.add(params);
            running = after;
        }
        jdbc.batchUpdate(insertSql, batchParams.toArray(MapSqlParameterSource[]::new));
    }

    private String matchKey(MatchDraft draft) {
        return String.join("|",
                String.valueOf(draft.matchSource()),
                String.valueOf(draft.systemRuleId()),
                String.valueOf(draft.rejectApplicationId()),
                String.valueOf(draft.trustScorePolicyId()),
                String.valueOf(draft.osReleaseLifecycleMasterId()),
                String.valueOf(draft.osLifecycleState()),
                String.valueOf(draft.deviceInstalledApplicationId()),
                String.valueOf(draft.severity()),
                String.valueOf(draft.complianceAction()),
                String.valueOf(draft.scoreDelta()),
                String.valueOf(draft.matchDetail()));
    }

    private String matchKey(PostureEvaluationMatch row) {
        return String.join("|",
                String.valueOf(row.getMatchSource()),
                String.valueOf(row.getSystemInformationRuleId()),
                String.valueOf(row.getRejectApplicationListId()),
                String.valueOf(row.getTrustScorePolicyId()),
                String.valueOf(row.getOsReleaseLifecycleMasterId()),
                String.valueOf(row.getOsLifecycleState()),
                String.valueOf(row.getDeviceInstalledApplicationId()),
                String.valueOf(row.getSeverity()),
                String.valueOf(row.getComplianceAction()),
                String.valueOf(row.getScoreDelta()),
                String.valueOf(row.getMatchDetail()));
    }

    private PostureEvaluationMatch buildMatchRow(Long runId, MatchDraft draft, OffsetDateTime now) {
        PostureEvaluationMatch row = new PostureEvaluationMatch();
        row.setPostureEvaluationRunId(runId);
        row.setMatchSource(draft.matchSource());
        row.setSystemInformationRuleId(draft.systemRuleId());
        row.setRejectApplicationListId(draft.rejectApplicationId());
        row.setTrustScorePolicyId(draft.trustScorePolicyId());
        row.setOsReleaseLifecycleMasterId(draft.osReleaseLifecycleMasterId());
        row.setOsLifecycleState(draft.osLifecycleState());
        row.setDeviceInstalledApplicationId(draft.deviceInstalledApplicationId());
        row.setMatched(true);
        row.setSeverity(draft.severity());
        row.setComplianceAction(draft.complianceAction());
        row.setScoreDelta(draft.scoreDelta());
        row.setMatchDetail(draft.matchDetail());
        row.setCreatedAt(now);
        row.setCreatedBy("rule-engine");
        return row;
    }

    private DeviceDecisionResponse createDecisionResponse(PostureEvaluationRun run,
                                                          DevicePosturePayload payload,
                                                          String responsePayload,
                                                          OffsetDateTime now) {
        DeviceDecisionResponse decisionResponse = new DeviceDecisionResponse();
        decisionResponse.setPostureEvaluationRunId(run.getId());
        decisionResponse.setTenantId(payload.getTenantId());
        decisionResponse.setDeviceExternalId(payload.getDeviceExternalId());
        decisionResponse.setDecisionAction(run.getDecisionAction());
        decisionResponse.setTrustScore(run.getTrustScoreAfter());
        decisionResponse.setRemediationRequired(run.isRemediationRequired());
        decisionResponse.setResponsePayload(responsePayload);
        decisionResponse.setDeliveryStatus("PENDING");
        decisionResponse.setSentAt(null);
        decisionResponse.setCreatedAt(now);
        decisionResponse.setCreatedBy("policy-service");
        return decisionResponse;
    }

    private DeviceDecisionResponse markDecisionDeliveredIfPending(DeviceDecisionResponse decision) {
        if (decision == null) {
            return null;
        }
        String currentStatus = canonicalDeliveryStatus(decision.getDeliveryStatus());
        if (currentStatus == null) {
            currentStatus = "PENDING";
        }

        boolean changed = !Objects.equals(currentStatus, decision.getDeliveryStatus());
        if (changed) {
            decision.setDeliveryStatus(currentStatus);
        }

        if ("PENDING".equals(currentStatus)) {
            decision.setDeliveryStatus("DELIVERED");
            if (decision.getSentAt() == null) {
                decision.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
            remediationService.markDelivered(decision.getPostureEvaluationRunId());
            changed = true;
        } else if (isAcknowledgedDeliveryStatus(currentStatus)
                && decision.getAcknowledgedAt() != null
                && decision.getSentAt() == null) {
            decision.setSentAt(decision.getAcknowledgedAt());
            changed = true;
        }

        return changed ? decisionRepository.save(decision) : decision;
    }

    private void updateProfileScores(DeviceTrustProfile profile, PostureEvaluationRun run, OffsetDateTime now) {
        profile.setCurrentScore(run.getTrustScoreAfter());
        profile.setScoreBand(scoreBandFor(run.getTrustScoreAfter()));
        profile.setPostureStatus(postureStatusFor(run.getDecisionAction()));
        profile.setLastEventAt(now);
        profile.setLastRecalculatedAt(now);
        profile.setModifiedAt(now);
        profile.setModifiedBy("rule-engine");
        profileRepository.save(profile);
    }

    private void auditRemediationRescanTransitions(DevicePosturePayload payload,
                                                   PostureEvaluationRun currentRun,
                                                   List<RemediationStatusTransition> transitions,
                                                   OffsetDateTime verifiedAt) {
        if (payload == null || currentRun == null || transitions == null || transitions.isEmpty()) {
            return;
        }

        for (RemediationStatusTransition transition : transitions) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("currentRunId", currentRun.getId());
            metadata.put("priorRunId", transition.postureEvaluationRunId());
            metadata.put("remediationRuleId", transition.remediationRuleId());
            metadata.put("sourceType", transition.sourceType());
            metadata.put("matchSource", transition.matchSource());
            metadata.put("fromStatus", transition.fromStatus());
            metadata.put("toStatus", transition.toStatus());
            metadata.put("verifiedAt", verifiedAt);

            auditEventService.recordBestEffort(
                    "REMEDIATION",
                    "REMEDIATION_STATUS_CHANGED",
                    "RESCAN_VERIFY",
                    payload.getTenantId(),
                    "rule-engine",
                    "POSTURE_EVALUATION_REMEDIATION",
                    transition.remediationId() == null ? null : String.valueOf(transition.remediationId()),
                    "SUCCESS",
                    metadata
            );
        }
    }

    private void markPayloadFailed(DevicePosturePayload payload, String errorMessage) {
        payloadFailureService.markPayloadFailed(payload, errorMessage, MAX_PROCESS_ERROR_LENGTH);
    }

    private void clearPayloadArtifacts(Long payloadId) {
        MapSqlParameterSource params = new MapSqlParameterSource("payloadId", payloadId);
        jdbc.update("DELETE FROM device_installed_application WHERE device_posture_payload_id = :payloadId", params);
        jdbc.update("DELETE FROM device_system_snapshot WHERE device_posture_payload_id = :payloadId", params);
    }

    private void clearRunArtifacts(Long runId) {
        MapSqlParameterSource params = new MapSqlParameterSource("runId", runId);
        jdbc.update("DELETE FROM posture_evaluation_remediation WHERE posture_evaluation_run_id = :runId", params);
        jdbc.update("DELETE FROM device_decision_response WHERE posture_evaluation_run_id = :runId", params);
        jdbc.update("DELETE FROM device_trust_score_event WHERE posture_evaluation_run_id = :runId", params);
        jdbc.update("DELETE FROM posture_evaluation_match WHERE posture_evaluation_run_id = :runId", params);
    }

    private short clampScore(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return (short) value;
    }

    private String scoreBandFor(short score) {
        if (score < 25) return "CRITICAL";
        if (score < 50) return "HIGH_RISK";
        if (score < 70) return "MEDIUM_RISK";
        if (score < 90) return "LOW_RISK";
        return "TRUSTED";
    }

    private String postureStatusFor(String decisionAction) {
        return "ALLOW".equalsIgnoreCase(decisionAction) ? "COMPLIANT" : "NON_COMPLIANT";
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private String normalizeStatus(String value) {
        if (value == null) {
            return "RECEIVED";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? "RECEIVED" : normalized;
    }
}
