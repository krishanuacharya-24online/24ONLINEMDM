package com.e24online.mdm.service;

import com.e24online.mdm.domain.*;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.e24online.mdm.utils.AgentWorkflowValueUtils.*;

/**
 * Legacy workflow retained for backward compatibility.
 * New agent posture execution path is {@link WorkflowOrchestrationService}.
 */
@Service
@Deprecated(since = "2026-03", forRemoval = false)
public class AgentPostureWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(AgentPostureWorkflowService.class);

    private static final Set<String> DECISION_ACTIONS = Set.of("ALLOW", "QUARANTINE", "BLOCK", "NOTIFY");
    private static final Set<String> OS_TYPES = Set.of("ANDROID", "IOS", "WINDOWS", "MACOS", "LINUX", "CHROMEOS", "FREEBSD", "OPENBSD");
    private static final Set<String> APP_OS_TYPES = Set.of("ANDROID", "IOS", "WINDOWS", "MACOS", "LINUX");
    private static final int MAX_PROCESS_ERROR_LENGTH = 900;
    private static final int MAX_INSTALLED_APPS = 5000;
    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_VERSION_LENGTH = 128;
    private static final int MAX_OS_CYCLE_LENGTH = 64;
    private static final int MAX_TIME_ZONE_LENGTH = 100;

    private final PostureIngestionService ingestService;
    private final DevicePosturePayloadRepository payloadRepository;
    private final DeviceTrustProfileRepository profileRepository;
    private final DeviceSystemSnapshotRepository snapshotRepository;
    private final DeviceInstalledApplicationRepository installedApplicationRepository;
    private final DeviceTrustScoreEventRepository scoreEventRepository;
    private final SystemInformationRuleRepository systemRuleRepository;
    private final SystemInformationRuleConditionRepository conditionRepository;
    private final RejectApplicationRepository rejectApplicationRepository;
    private final TrustScorePolicyRepository trustScorePolicyRepository;
    private final TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    private final RuleRemediationMappingRepository ruleRemediationMappingRepository;
    private final RemediationRuleRepository remediationRuleRepository;
    private final PostureEvaluationRunRepository runRepository;
    private final PostureEvaluationMatchRepository matchRepository;
    private final PostureEvaluationRemediationRepository remediationRepository;
    private final DeviceDecisionResponseRepository decisionRepository;
    private final OsReleaseLifecycleMasterRepository osLifecycleRepository;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;
    private final BlockingDb blockingDb;
    private final NamedParameterJdbcTemplate jdbc;
    private final PayloadFailureService payloadFailureService;

    public AgentPostureWorkflowService(PostureIngestionService ingestService,
                                       DevicePosturePayloadRepository payloadRepository,
                                       DeviceTrustProfileRepository profileRepository,
                                       DeviceSystemSnapshotRepository snapshotRepository,
                                       DeviceInstalledApplicationRepository installedApplicationRepository,
                                       DeviceTrustScoreEventRepository scoreEventRepository,
                                       SystemInformationRuleRepository systemRuleRepository,
                                       SystemInformationRuleConditionRepository conditionRepository,
                                       RejectApplicationRepository rejectApplicationRepository,
                                       TrustScorePolicyRepository trustScorePolicyRepository,
                                       TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository,
                                       RuleRemediationMappingRepository ruleRemediationMappingRepository,
                                       RemediationRuleRepository remediationRuleRepository,
                                       PostureEvaluationRunRepository runRepository,
                                       PostureEvaluationMatchRepository matchRepository,
                                       PostureEvaluationRemediationRepository remediationRepository,
                                       DeviceDecisionResponseRepository decisionRepository,
                                       OsReleaseLifecycleMasterRepository osLifecycleRepository,
                                       AuditEventService auditEventService,
                                       ObjectMapper objectMapper,
                                       BlockingDb blockingDb,
                                       NamedParameterJdbcTemplate jdbc,
                                       PayloadFailureService payloadFailureService) {
        this.ingestService = ingestService;
        this.payloadRepository = payloadRepository;
        this.profileRepository = profileRepository;
        this.snapshotRepository = snapshotRepository;
        this.installedApplicationRepository = installedApplicationRepository;
        this.scoreEventRepository = scoreEventRepository;
        this.systemRuleRepository = systemRuleRepository;
        this.conditionRepository = conditionRepository;
        this.rejectApplicationRepository = rejectApplicationRepository;
        this.trustScorePolicyRepository = trustScorePolicyRepository;
        this.trustScoreDecisionPolicyRepository = trustScoreDecisionPolicyRepository;
        this.ruleRemediationMappingRepository = ruleRemediationMappingRepository;
        this.remediationRuleRepository = remediationRuleRepository;
        this.runRepository = runRepository;
        this.matchRepository = matchRepository;
        this.remediationRepository = remediationRepository;
        this.decisionRepository = decisionRepository;
        this.osLifecycleRepository = osLifecycleRepository;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
        this.blockingDb = blockingDb;
        this.jdbc = jdbc;
        this.payloadFailureService = payloadFailureService;
    }

    public Mono<PosturePayloadIngestResponse> ingestAndEvaluateAsync(String tenantId, PosturePayloadIngestRequest request) {
        log.info("ingestAndEvaluateAsync");
        log.info("tenantId :: {}", tenantId);
        log.info("deviceExternalId :: {}, agentId :: {}", request.getDeviceExternalId(), request.getAgentId());
        return blockingDb.mono(() -> ingestAndEvaluate(tenantId, request));
    }

    @Transactional
    public PosturePayloadIngestResponse ingestAndEvaluate(String tenantId, PosturePayloadIngestRequest request) {
        log.info("ingestAndEvaluate");
        Long payloadId = ingestService.ingest(tenantId, request);
        DevicePosturePayload payload = payloadRepository.findById(payloadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found after ingest"));

        try {
            PostureEvaluationRun existingRun = runRepository.findOneByPayloadId(payloadId).orElse(null);
            JsonNode root = parsePayloadJson(payload.getPayloadJson());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ParsedPosture parsed = parsePosture(root, request, payload, now);

            DeviceTrustProfile profile = upsertTrustProfile(tenantId, parsed, now);

            if (existingRun != null) {
                clearRunArtifacts(existingRun.getId());
            }
            clearPayloadArtifacts(payloadId);

            payload.setProcessStatus("VALIDATED");
            payload.setProcessedAt(now);
            payload.setProcessError(null);
            payloadRepository.save(payload);

            DeviceSystemSnapshot snapshot = saveSnapshot(payload, profile, parsed);
            List<DeviceInstalledApplication> installedApps = saveInstalledApps(payload, profile, parsed, now);

            LifecycleResolution lifecycle = resolveLifecycle(parsed, now.toLocalDate());
            applyLifecycle(profile, snapshot, lifecycle);
            profileRepository.save(profile);
            snapshotRepository.save(snapshot);

            EvaluationComputation computed = computeEvaluation(profile, parsed, installedApps, lifecycle, now);

            PostureEvaluationRun run = existingRun != null ? existingRun : new PostureEvaluationRun();
            populateRun(run, payload, profile, lifecycle, computed, now);
            PostureEvaluationRun savedRun = runRepository.save(run);

            List<SavedMatch> savedMatches = saveMatches(savedRun.getId(), computed.matches(), now);
            saveScoreEvents(savedRun.getId(), profile.getId(), computed.scoreSignals(), computed.scoreBefore(), now);

            List<SavedRemediation> savedRemediations = saveRemediations(savedRun, savedMatches, parsed, now);

            String responsePayload = buildDecisionPayload(savedRun, savedRemediations);
            savedRun.setResponsePayload(responsePayload);
            savedRun.setRespondedAt(now);
            savedRun = runRepository.save(savedRun);

            DeviceDecisionResponse decisionResponse = new DeviceDecisionResponse();
            decisionResponse.setPostureEvaluationRunId(savedRun.getId());
            decisionResponse.setTenantId(payload.getTenantId());
            decisionResponse.setDeviceExternalId(payload.getDeviceExternalId());
            decisionResponse.setDecisionAction(savedRun.getDecisionAction());
            decisionResponse.setTrustScore(savedRun.getTrustScoreAfter());
            decisionResponse.setRemediationRequired(savedRun.isRemediationRequired());
            decisionResponse.setResponsePayload(responsePayload);
            decisionResponse.setDeliveryStatus("PENDING");
            decisionResponse.setSentAt(null);
            decisionResponse.setCreatedAt(now);
            decisionResponse.setCreatedBy("policy-service");
            DeviceDecisionResponse savedDecision = decisionRepository.save(decisionResponse);

            profile.setCurrentScore(savedRun.getTrustScoreAfter());
            profile.setScoreBand(scoreBandFor(savedRun.getTrustScoreAfter()));
            profile.setPostureStatus(postureStatusFor(savedRun.getDecisionAction()));
            profile.setLastEventAt(now);
            profile.setLastRecalculatedAt(now);
            profile.setModifiedAt(now);
            profile.setModifiedBy("rule-engine");
            profileRepository.save(profile);

            payload.setProcessStatus("EVALUATED");
            payload.setProcessedAt(now);
            payload.setProcessError(null);
            payloadRepository.save(payload);

            auditLegacyEvaluationEvent(
                    payload,
                    savedRun.getId(),
                    savedRun.getDecisionAction(),
                    savedRun.getTrustScoreAfter(),
                    "SUCCESS",
                    null
            );

            return buildResponse(payload, savedRun, savedDecision, savedRemediations);
        } catch (ResponseStatusException ex) {
            auditLegacyEvaluationEvent(payload, null, null, null, "FAILURE", ex.getReason());
            markPayloadFailed(payload, ex.getReason());
            throw ex;
        } catch (Exception ex) {
            log.error("Posture evaluation failed for payloadId={} tenantId={} deviceExternalId={}",
                    payload.getId(), payload.getTenantId(), payload.getDeviceExternalId(), ex);
            auditLegacyEvaluationEvent(payload, null, null, null, "FAILURE", ex.getMessage());
            markPayloadFailed(payload, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posture evaluation failed");
        }
    }

    private void auditLegacyEvaluationEvent(DevicePosturePayload payload,
                                            Long evaluationRunId,
                                            String decisionAction,
                                            Short trustScore,
                                            String status,
                                            String reason) {
        if (payload == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", "legacy-agent-workflow");
        metadata.put("evaluationRunId", evaluationRunId);
        metadata.put("decisionAction", decisionAction);
        metadata.put("trustScore", trustScore);
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditEventService.recordBestEffort(
                "POSTURE",
                "POSTURE_EVALUATED",
                "EVALUATE",
                payload.getTenantId(),
                "rule-engine",
                "DEVICE_POSTURE_PAYLOAD",
                payload.getId() == null ? null : String.valueOf(payload.getId()),
                status,
                metadata
        );
    }

    private void markPayloadFailed(DevicePosturePayload payload, String errorMessage) {
        payloadFailureService.markPayloadFailed(payload, errorMessage, MAX_PROCESS_ERROR_LENGTH);
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
        } catch (JacksonException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload_json");
        }
    }

    private ParsedPosture parsePosture(JsonNode root,
                                       PosturePayloadIngestRequest request,
                                       DevicePosturePayload payload,
                                       OffsetDateTime now) {
        String osType = normalizeUpper(text(root, "os_type"));
        if (osType == null || !OS_TYPES.contains(osType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload_json.os_type is required");
        }
        String osName = normalizeUpper(trimToNullAndCap(text(root, "os_name"), MAX_TEXT_LENGTH));
        String osVersion = trimToNullAndCap(text(root, "os_version"), MAX_VERSION_LENGTH);
        String osCycle = trimToNullAndCap(text(root, "os_cycle"), MAX_OS_CYCLE_LENGTH);
        String deviceType = normalizeUpper(trimToNullAndCap(text(root, "device_type"), MAX_TEXT_LENGTH));
        String timeZone = trimToNullAndCap(text(root, "time_zone"), MAX_TIME_ZONE_LENGTH);
        String kernelVersion = trimToNullAndCap(text(root, "kernel_version"), MAX_VERSION_LENGTH);
        Integer apiLevel = intValue(root.get("api_level"));
        String osBuildNumber = trimToNullAndCap(text(root, "os_build_number"), MAX_VERSION_LENGTH);
        String manufacturer = trimToNullAndCap(text(root, "manufacturer"), MAX_TEXT_LENGTH);
        Boolean rootDetected = boolValue(root.get("root_detected"));
        Boolean emulator = boolValue(root.get("running_on_emulator"));
        Boolean usbDebugging = boolValue(root.get("usb_debugging_status"));
        OffsetDateTime captureTime = parseOffsetDateTime(text(root, "capture_time")).orElse(now);
        JsonNode appsNode = root.get("installed_apps");
        if (appsNode != null && appsNode.isArray() && appsNode.size() > MAX_INSTALLED_APPS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "payload_json.installed_apps exceeds max allowed size of " + MAX_INSTALLED_APPS
            );
        }

        return new ParsedPosture(
                payload.getTenantId(),
                request.getDeviceExternalId(),
                request.getAgentId(),
                osType,
                osName,
                osVersion,
                osCycle,
                deviceType,
                timeZone,
                kernelVersion,
                apiLevel,
                osBuildNumber,
                manufacturer,
                rootDetected,
                emulator,
                usbDebugging,
                captureTime,
                root,
                appsNode != null && appsNode.isArray() ? appsNode : objectMapper.createArrayNode()
        );
    }

    private DeviceTrustProfile upsertTrustProfile(String tenantId, ParsedPosture parsed, OffsetDateTime now) {
        DeviceTrustProfile profile = profileRepository.findActiveByTenantAndDevice(tenantId, parsed.deviceExternalId()).orElse(null);
        if (profile == null) {
            profile = new DeviceTrustProfile();
            profile.setTenantId(tenantId);
            profile.setDeviceExternalId(parsed.deviceExternalId());
            profile.setCurrentScore((short) 100);
            profile.setScoreBand("TRUSTED");
            profile.setPostureStatus("COMPLIANT");
            profile.setDeleted(false);
            profile.setCreatedAt(now);
            profile.setCreatedBy("posture-parser");
        }

        profile.setDeviceType(validDeviceType(parsed.deviceType()));
        profile.setOsType(parsed.osType());
        profile.setOsName(parsed.osName());
        if (profile.getOsLifecycleState() == null || profile.getOsLifecycleState().isBlank()) {
            profile.setOsLifecycleState("NOT_TRACKED");
        }
        if (profile.getLastRecalculatedAt() == null) {
            profile.setLastRecalculatedAt(now);
        }
        profile.setModifiedAt(now);
        profile.setModifiedBy("posture-parser");
        return profileRepository.save(profile);
    }

    private DeviceSystemSnapshot saveSnapshot(DevicePosturePayload payload,
                                              DeviceTrustProfile profile,
                                              ParsedPosture parsed) {
        if (!snapshotExists(payload.getId())) {
            jdbc.update(
                    "UPDATE device_system_snapshot SET is_latest = false WHERE device_trust_profile_id = :profileId AND is_latest = true",
                    new MapSqlParameterSource("profileId", profile.getId())
            );

            DeviceSystemSnapshot snapshot = new DeviceSystemSnapshot();
            snapshot.setDevicePosturePayloadId(payload.getId());
            snapshot.setDeviceTrustProfileId(profile.getId());
            snapshot.setCaptureTime(parsed.captureTime());
            snapshot.setDeviceType(validDeviceType(parsed.deviceType()));
            snapshot.setOsType(parsed.osType());
            snapshot.setOsName(parsed.osName());
            snapshot.setOsCycle(parsed.osCycle());
            snapshot.setOsVersion(parsed.osVersion());
            snapshot.setTimeZone(parsed.timeZone());
            snapshot.setKernelVersion(parsed.kernelVersion());
            snapshot.setApiLevel(parsed.apiLevel());
            snapshot.setOsBuildNumber(parsed.osBuildNumber());
            snapshot.setManufacturer(parsed.manufacturer());
            snapshot.setRootDetected(parsed.rootDetected());
            snapshot.setRunningOnEmulator(parsed.runningOnEmulator());
            snapshot.setUsbDebuggingStatus(parsed.usbDebuggingStatus());
            snapshot.setLatest(true);
            return snapshotRepository.save(snapshot);
        }
        return loadSnapshotByPayload(payload.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snapshot not found for existing payload"));
    }

    private List<DeviceInstalledApplication> saveInstalledApps(DevicePosturePayload payload,
                                                               DeviceTrustProfile profile,
                                                               ParsedPosture parsed,
                                                               OffsetDateTime now) {
        if (installedAppsExist(payload.getId())) {
            return loadInstalledAppsByPayload(payload.getId());
        }

        List<DeviceInstalledApplication> saved = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (JsonNode appNode : parsed.installedApps()) {
            String appName = trimToNullAndCap(text(appNode, "app_name"), MAX_TEXT_LENGTH);
            if (appName == null) {
                continue;
            }
            String appOsType = normalizeUpper(firstNonBlank(
                    trimToNullAndCap(text(appNode, "app_os_type"), MAX_TEXT_LENGTH),
                    trimToNullAndCap(text(appNode, "os_type"), MAX_TEXT_LENGTH),
                    parsed.osType()
            ));
            if (appOsType == null || !APP_OS_TYPES.contains(appOsType)) {
                continue;
            }
            String packageId = trimToNullAndCap(text(appNode, "package_id"), MAX_TEXT_LENGTH);
            String dedupeKey = appOsType + "::" + (packageId == null ? "" : packageId.toLowerCase(Locale.ROOT)) + "::" + appName.toLowerCase(Locale.ROOT);
            if (!dedupe.add(dedupeKey)) {
                continue;
            }

            String status = normalizeUpper(firstNonBlank(trimToNullAndCap(text(appNode, "status"), MAX_TEXT_LENGTH), "ACTIVE"));
            if (!Set.of("ACTIVE", "REMOVED", "UNKNOWN").contains(status)) {
                status = "ACTIVE";
            }

            DeviceInstalledApplication app = new DeviceInstalledApplication();
            app.setDevicePosturePayloadId(payload.getId());
            app.setDeviceTrustProfileId(profile.getId());
            app.setCaptureTime(parsed.captureTime());
            app.setAppName(appName);
            app.setPublisher(trimToNullAndCap(text(appNode, "publisher"), MAX_TEXT_LENGTH));
            app.setPackageId(packageId);
            app.setAppOsType(appOsType);
            app.setAppVersion(trimToNullAndCap(text(appNode, "app_version"), MAX_VERSION_LENGTH));
            app.setLatestAvailableVersion(trimToNullAndCap(text(appNode, "latest_available_version"), MAX_VERSION_LENGTH));
            app.setSystemApp(boolValue(appNode.get("is_system_app")));
            app.setInstallSource(trimToNullAndCap(text(appNode, "install_source"), MAX_TEXT_LENGTH));
            app.setStatus(status);
            app.setCreatedAt(now);
            app.setCreatedBy("posture-parser");
            saved.add(installedApplicationRepository.save(app));
        }
        return saved;
    }

    private LifecycleResolution resolveLifecycle(ParsedPosture parsed, LocalDate today) {
        String normalizedOsName = normalizeUpper(parsed.osName());
        String cycle = trimToNull(parsed.osCycle());
        if (cycle == null) {
            cycle = deriveCycle(parsed.osVersion());
        }
        if (cycle == null) {
            return new LifecycleResolution(null, "NOT_TRACKED", "OS_NOT_TRACKED");
        }
        final String cycleKey = cycle;

        List<OsReleaseLifecycleMaster> all = toList(osLifecycleRepository.findAll());
        Optional<OsReleaseLifecycleMaster> match = all.stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .filter(x -> equalsIgnoreCase(x.getOsType(), parsed.osType()))
                .filter(x -> {
                    if (x.getOsName() == null || x.getOsName().isBlank()) {
                        return true;
                    }
                    return equalsIgnoreCase(x.getOsName(), normalizedOsName);
                })
                .filter(x -> equalsIgnoreCase(x.getCycle(), cycleKey))
                .findFirst();

        if (match.isEmpty()) {
            return new LifecycleResolution(null, "NOT_TRACKED", "OS_NOT_TRACKED");
        }
        OsReleaseLifecycleMaster row = match.get();
        String state = "SUPPORTED";
        if ("NOT_FOUND".equalsIgnoreCase(row.getSupportState())) {
            state = "NOT_TRACKED";
        } else if (row.getEeolOn() != null && today.isAfter(row.getEeolOn())) {
            state = "EEOL";
        } else if (row.getEolOn() != null && today.isAfter(row.getEolOn())) {
            state = "EOL";
        }
        return new LifecycleResolution(row.getId(), state, lifecycleSignalFor(state));
    }

    private void applyLifecycle(DeviceTrustProfile profile, DeviceSystemSnapshot snapshot, LifecycleResolution lifecycle) {
        profile.setOsLifecycleState(lifecycle.state());
        profile.setOsReleaseLifecycleMasterId(lifecycle.masterId());
        snapshot.setOsReleaseLifecycleMasterId(lifecycle.masterId());
    }

    private EvaluationComputation computeEvaluation(DeviceTrustProfile profile,
                                                    ParsedPosture parsed,
                                                    List<DeviceInstalledApplication> installedApps,
                                                    LifecycleResolution lifecycle,
                                                    OffsetDateTime now) {
        List<SystemInformationRule> activeRules = activeSystemRules(parsed, now);
        Map<Long, List<SystemInformationRuleCondition>> conditionsByRule = activeRuleConditions();
        List<RejectApplication> activeRejectApps = activeRejectApps(parsed.tenantId(), parsed.osType(), now);
        List<TrustScorePolicy> activePolicies = activeTrustPolicies(parsed.tenantId(), now);

        List<MatchDraft> matches = new ArrayList<>();
        List<ScoreSignal> signals = new ArrayList<>();

        int matchedRuleCount = 0;
        for (SystemInformationRule rule : activeRules) {
            List<SystemInformationRuleCondition> conditions = conditionsByRule.getOrDefault(rule.getId(), List.of());
            if (!matchesSystemRule(rule, conditions, parsed)) {
                continue;
            }
            AppliedPolicy applied = findAppliedPolicy(
                    activePolicies,
                    "SYSTEM_RULE",
                    List.of(rule.getRuleCode(), rule.getRuleTag()),
                    rule.getSeverity(),
                    rule.getComplianceAction(),
                    parsed.tenantId()
            );
            short delta = applied != null ? weightedDelta(applied.policy()) : safeShort(rule.getRiskScoreDelta(), (short) 0);
            matches.add(new MatchDraft(
                    "SYSTEM_RULE",
                    rule.getId(),
                    null,
                    null,
                    null,
                    lifecycle.state(),
                    null,
                    safeShort(rule.getSeverity(), null),
                    normalizeDecision(rule.getComplianceAction()),
                    delta,
                    toJson(Map.of("rule_code", safeText(rule.getRuleCode()), "rule_tag", safeText(rule.getRuleTag())))
            ));
            signals.add(new ScoreSignal(
                    "SYSTEM_RULE",
                    rule.getId(),
                    applied == null ? null : applied.policy().getId(),
                    rule.getId(),
                    null,
                    lifecycle.masterId(),
                    lifecycle.state(),
                    delta,
                    "Matched system rule: " + safeText(rule.getRuleCode())
            ));
            matchedRuleCount++;
        }

        int matchedAppCount = 0;
        for (DeviceInstalledApplication app : installedApps) {
            for (RejectApplication reject : activeRejectApps) {
                if (!matchesRejectApp(app, reject)) {
                    continue;
                }
                AppliedPolicy applied = findAppliedPolicy(
                        activePolicies,
                        "REJECT_APPLICATION",
                        List.of(reject.getPackageId(), reject.getAppName(), reject.getPolicyTag()),
                        reject.getSeverity(),
                        "BLOCK",
                        parsed.tenantId()
                );
                short delta = applied != null ? weightedDelta(applied.policy()) : defaultRejectDelta(safeShort(reject.getSeverity(), (short) 3));
                matches.add(new MatchDraft(
                        "REJECT_APPLICATION",
                        null,
                        reject.getId(),
                        null,
                        null,
                        lifecycle.state(),
                        app.getId(),
                        safeShort(reject.getSeverity(), null),
                        "BLOCK",
                        delta,
                        toJson(Map.of("app_name", safeText(app.getAppName()), "package_id", safeText(app.getPackageId())))
                ));
                signals.add(new ScoreSignal(
                        "REJECT_APPLICATION",
                        reject.getId(),
                        applied == null ? null : applied.policy().getId(),
                        null,
                        reject.getId(),
                        lifecycle.masterId(),
                        lifecycle.state(),
                        delta,
                        "Matched rejected app: " + safeText(app.getAppName())
                ));
                matchedAppCount++;
            }
        }

        if (!"SUPPORTED".equalsIgnoreCase(lifecycle.state())) {
            AppliedPolicy applied = findAppliedPolicy(activePolicies, "POSTURE_SIGNAL", List.of(lifecycle.signalKey()), null, null, parsed.tenantId());
            short delta = applied != null ? weightedDelta(applied.policy()) : defaultLifecycleDelta(lifecycle.state());
            signals.add(new ScoreSignal(
                    "POSTURE_SIGNAL",
                    lifecycle.masterId(),
                    applied == null ? null : applied.policy().getId(),
                    null,
                    null,
                    lifecycle.masterId(),
                    lifecycle.state(),
                    delta,
                    "Lifecycle posture signal: " + lifecycle.signalKey()
            ));
            if (applied != null) {
                matches.add(new MatchDraft(
                        "TRUST_POLICY",
                        null,
                        null,
                        applied.policy().getId(),
                        lifecycle.masterId(),
                        lifecycle.state(),
                        null,
                        null,
                        null,
                        delta,
                        toJson(Map.of("signal_key", lifecycle.signalKey()))
                ));
            }
        }

        short before = safeShort(profile.getCurrentScore(), (short) 100);
        short running = before;
        for (ScoreSignal signal : signals) {
            running = clampScore(running + signal.scoreDelta());
        }
        short after = running;
        short deltaTotal = (short) (after - before);

        Optional<TrustScoreDecisionPolicy> decisionPolicy = trustScoreDecisionPolicyRepository.findActivePolicyForScore(parsed.tenantId(), after, now);
        String decisionAction = decisionPolicy
                .map(TrustScoreDecisionPolicy::getDecisionAction)
                .map(x -> x.toUpperCase(Locale.ROOT))
                .orElseGet(() -> defaultDecisionForScore(after));
        boolean remediationRequired = decisionPolicy
                .map(TrustScoreDecisionPolicy::isRemediationRequired)
                .orElse(!"ALLOW".equalsIgnoreCase(decisionAction));
        String decisionReason = decisionPolicy
                .map(TrustScoreDecisionPolicy::getResponseMessage)
                .filter(x -> !x.isBlank())
                .orElse("Auto decision from evaluated trust score");

        return new EvaluationComputation(
                before,
                after,
                deltaTotal,
                matchedRuleCount,
                matchedAppCount,
                decisionAction,
                decisionReason,
                remediationRequired,
                decisionPolicy.map(TrustScoreDecisionPolicy::getId).orElse(null),
                matches,
                signals
        );
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
        List<SavedMatch> saved = new ArrayList<>();
        for (MatchDraft draft : matchDrafts) {
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
            PostureEvaluationMatch persisted = matchRepository.save(row);
            saved.add(new SavedMatch(persisted, draft));
        }
        return saved;
    }

    private void saveScoreEvents(Long runId,
                                 Long profileId,
                                 List<ScoreSignal> signals,
                                 short scoreBefore,
                                 OffsetDateTime now) {
        short running = scoreBefore;
        for (ScoreSignal signal : signals) {
            short before = running;
            short after = clampScore(before + signal.scoreDelta());

            DeviceTrustScoreEvent event = new DeviceTrustScoreEvent();
            event.setDeviceTrustProfileId(profileId);
            event.setEventSource(signal.eventSource());
            event.setSourceRecordId(signal.sourceRecordId());
            event.setTrustScorePolicyId(signal.trustScorePolicyId());
            event.setSystemInformationRuleId(signal.systemRuleId());
            event.setRejectApplicationListId(signal.rejectApplicationId());
            event.setOsReleaseLifecycleMasterId(signal.osReleaseLifecycleMasterId());
            event.setOsLifecycleState(signal.osLifecycleState());
            event.setScoreBefore(before);
            event.setScoreDelta(signal.scoreDelta());
            event.setScoreAfter(after);
            event.setEventTime(now);
            event.setProcessedAt(now);
            event.setProcessedBy("rule-engine");
            event.setNotes(signal.notes());
            DeviceTrustScoreEvent persisted = scoreEventRepository.save(event);

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("eventId", persisted.getId());
            jdbc.update(
                    "UPDATE device_trust_score_event SET posture_evaluation_run_id = :runId WHERE id = :eventId",
                    params
            );

            running = after;
        }
    }

    private List<SavedRemediation> saveRemediations(PostureEvaluationRun run,
                                                    List<SavedMatch> matches,
                                                    ParsedPosture posture,
                                                    OffsetDateTime now) {
        List<RuleRemediationMapping> mappings = activeMappings(posture.tenantId(), now);
        Map<Long, RemediationRule> remediationById = activeRemediationRules(posture.tenantId(), now).stream()
                .collect(Collectors.toMap(RemediationRule::getId, x -> x));

        List<RemediationCandidate> candidates = new ArrayList<>();
        for (SavedMatch savedMatch : matches) {
            MatchDraft draft = savedMatch.draft();
            List<RuleRemediationMapping> mapped = mappingsForMatch(mappings, draft);
            for (RuleRemediationMapping mapping : mapped) {
                candidates.add(new RemediationCandidate(mapping, savedMatch.match().getId(), "MATCH"));
            }
        }

        for (RuleRemediationMapping mapping : mappings) {
            if (!"DECISION".equalsIgnoreCase(mapping.getSourceType())) {
                continue;
            }
            if (!equalsIgnoreCase(mapping.getDecisionAction(), run.getDecisionAction())) {
                continue;
            }
            candidates.add(new RemediationCandidate(mapping, null, "DECISION"));
        }

        candidates.sort(Comparator.comparingInt(x -> x.mapping().getRankOrder() == null ? Integer.MAX_VALUE : x.mapping().getRankOrder()));

        Set<String> dedupe = new HashSet<>();
        List<SavedRemediation> saved = new ArrayList<>();
        for (RemediationCandidate candidate : candidates) {
            RuleRemediationMapping mapping = candidate.mapping();
            Long remediationRuleId = mapping.getRemediationRuleId();
            RemediationRule rule = remediationById.get(remediationRuleId);
            if (!matchesRemediationTarget(rule, posture)) {
                continue;
            }
            String dedupeKey = run.getId() + "|" + remediationRuleId + "|" + candidate.matchId();
            if (!dedupe.add(dedupeKey)) {
                continue;
            }

            PostureEvaluationRemediation row = new PostureEvaluationRemediation();
            row.setPostureEvaluationRunId(run.getId());
            row.setRemediationRuleId(remediationRuleId);
            row.setPostureEvaluationMatchId(candidate.matchId());
            row.setSourceType(candidate.sourceType());
            row.setRemediationStatus("PROPOSED");
            row.setInstructionOverride(rule.getInstructionJson());
            row.setCreatedAt(now);
            row.setCreatedBy("rule-engine");
            PostureEvaluationRemediation persisted = remediationRepository.save(row);

            saved.add(new SavedRemediation(
                    persisted,
                    rule,
                    mapping.getEnforceMode()
            ));
        }
        return saved;
    }

    private String buildDecisionPayload(PostureEvaluationRun run, List<SavedRemediation> remediations) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluation_run_id", run.getId());
        payload.put("decision_action", run.getDecisionAction());
        payload.put("trust_score", run.getTrustScoreAfter());
        payload.put("decision_reason", run.getDecisionReason());
        payload.put("remediation_required", run.isRemediationRequired());
        payload.put("remediations", toRemediationPayload(remediations));
        return toJson(payload);
    }

    private PosturePayloadIngestResponse buildResponse(DevicePosturePayload payload,
                                                       PostureEvaluationRun run,
                                                       DeviceDecisionResponse decision,
                                                       List<SavedRemediation> remediations) {
        return new PosturePayloadIngestResponse(
                payload.getId(),
                payload.getProcessStatus(),
                run.getId(),
                decision.getId(),
                decision.getDecisionAction(),
                decision.getTrustScore(),
                run.getDecisionReason(),
                run.isRemediationRequired(),
                remediations.stream().map(this::toRemediationSummary).toList()
        );
    }

    private List<SavedRemediation> loadResponseRemediations(Long runId) {
        List<PostureEvaluationRemediation> rows = remediationRepository.findByRunId(runId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, RemediationRule> rules = toList(remediationRuleRepository.findAll()).stream()
                .collect(Collectors.toMap(RemediationRule::getId, x -> x));
        List<SavedRemediation> result = new ArrayList<>();
        for (PostureEvaluationRemediation row : rows) {
            RemediationRule rule = rules.get(row.getRemediationRuleId());
            if (rule == null) {
                continue;
            }
            result.add(new SavedRemediation(row, rule, null));
        }
        return result;
    }

    private List<Map<String, Object>> toRemediationPayload(List<SavedRemediation> remediations) {
        return remediations.stream().map(this::toRemediationMap).toList();
    }

    private Map<String, Object> toRemediationMap(SavedRemediation saved) {
        PostureEvaluationRemediation r = saved.remediation();
        RemediationRule rule = saved.rule();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("evaluation_remediation_id", r.getId());
        map.put("remediation_rule_id", rule.getId());
        map.put("remediation_code", rule.getRemediationCode());
        map.put("title", rule.getTitle());
        map.put("description", rule.getDescription());
        map.put("remediation_type", rule.getRemediationType());
        map.put("enforce_mode", saved.enforceMode());
        map.put("instruction", safeText(r.getInstructionOverride() != null ? r.getInstructionOverride() : rule.getInstructionJson()));
        map.put("status", r.getRemediationStatus());
        return map;
    }

    private RemediationSummary toRemediationSummary(SavedRemediation saved) {
        PostureEvaluationRemediation row = saved.remediation();
        RemediationRule rule = saved.rule();
        return new RemediationSummary(
                row.getId(),
                rule.getId(),
                rule.getRemediationCode(),
                rule.getTitle(),
                rule.getDescription(),
                rule.getRemediationType(),
                saved.enforceMode(),
                row.getInstructionOverride() != null ? row.getInstructionOverride() : rule.getInstructionJson(),
                row.getRemediationStatus()
        );
    }

    private List<RuleRemediationMapping> mappingsForMatch(List<RuleRemediationMapping> mappings, MatchDraft draft) {
        List<RuleRemediationMapping> out = new ArrayList<>();
        for (RuleRemediationMapping mapping : mappings) {
            String source = normalizeUpper(mapping.getSourceType());
            if ("SYSTEM_RULE".equals(source) && "SYSTEM_RULE".equals(draft.matchSource())
                    && Objects.equals(mapping.getSystemInformationRuleId(), draft.systemRuleId())) {
                out.add(mapping);
            } else if ("REJECT_APPLICATION".equals(source) && "REJECT_APPLICATION".equals(draft.matchSource())
                    && Objects.equals(mapping.getRejectApplicationListId(), draft.rejectApplicationId())) {
                out.add(mapping);
            } else if ("TRUST_POLICY".equals(source) && "TRUST_POLICY".equals(draft.matchSource())
                    && Objects.equals(mapping.getTrustScorePolicyId(), draft.trustScorePolicyId())) {
                out.add(mapping);
            }
        }
        return out;
    }

    private boolean matchesRemediationTarget(RemediationRule rule, ParsedPosture posture) {
        if (rule == null || rule.isDeleted() || !"ACTIVE".equalsIgnoreCase(rule.getStatus())) {
            return false;
        }
        if (rule.getEffectiveFrom() != null && rule.getEffectiveFrom().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            return false;
        }
        if (rule.getEffectiveTo() != null && !rule.getEffectiveTo().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            return false;
        }
        if (rule.getOsType() != null && !rule.getOsType().isBlank() && !equalsIgnoreCase(rule.getOsType(), posture.osType())) {
            return false;
        }
        return rule.getDeviceType() == null || rule.getDeviceType().isBlank() || equalsIgnoreCase(rule.getDeviceType(), posture.deviceType());
    }

    private List<SystemInformationRule> activeSystemRules(ParsedPosture parsed, OffsetDateTime now) {
        return toList(systemRuleRepository.findAll()).stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .filter(x -> isInPolicyScope(x.getTenantId(), parsed.tenantId()))
                .filter(x -> inEffectiveWindow(x.getEffectiveFrom(), x.getEffectiveTo(), now))
                .filter(x -> equalsIgnoreCase(x.getOsType(), parsed.osType()))
                .filter(x -> x.getOsName() == null || x.getOsName().isBlank() || equalsIgnoreCase(x.getOsName(), parsed.osName()))
                .filter(x -> x.getDeviceType() == null || x.getDeviceType().isBlank() || equalsIgnoreCase(x.getDeviceType(), parsed.deviceType()))
                .sorted(Comparator.comparingInt(x -> x.getPriority() == null ? Integer.MAX_VALUE : x.getPriority()))
                .toList();
    }

    private Map<Long, List<SystemInformationRuleCondition>> activeRuleConditions() {
        List<SystemInformationRuleCondition> all = toList(conditionRepository.findAll()).stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .toList();
        Map<Long, List<SystemInformationRuleCondition>> out = new HashMap<>();
        for (SystemInformationRuleCondition c : all) {
            out.computeIfAbsent(c.getSystemInformationRuleId(), _ -> new ArrayList<>()).add(c);
        }
        return out;
    }

    private List<RejectApplication> activeRejectApps(String tenantId, String osType, OffsetDateTime now) {
        return toList(rejectApplicationRepository.findAll()).stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .filter(x -> isInPolicyScope(x.getTenantId(), tenantId))
                .filter(x -> equalsIgnoreCase(x.getAppOsType(), osType))
                .filter(x -> inEffectiveWindow(x.getEffectiveFrom(), x.getEffectiveTo(), now))
                .toList();
    }

    private List<TrustScorePolicy> activeTrustPolicies(String tenantId, OffsetDateTime now) {
        return toList(trustScorePolicyRepository.findAll()).stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .filter(x -> isInPolicyScope(x.getTenantId(), tenantId))
                .filter(x -> inEffectiveWindow(x.getEffectiveFrom(), x.getEffectiveTo(), now))
                .toList();
    }

    private List<RuleRemediationMapping> activeMappings(String tenantId, OffsetDateTime now) {
        return toList(ruleRemediationMappingRepository.findAll()).stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .filter(x -> isInPolicyScope(x.getTenantId(), tenantId))
                .filter(x -> inEffectiveWindow(x.getEffectiveFrom(), x.getEffectiveTo(), now))
                .toList();
    }

    private List<RemediationRule> activeRemediationRules(String tenantId, OffsetDateTime now) {
        return toList(remediationRuleRepository.findAll()).stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .filter(x -> isInPolicyScope(x.getTenantId(), tenantId))
                .filter(x -> inEffectiveWindow(x.getEffectiveFrom(), x.getEffectiveTo(), now))
                .toList();
    }

    private boolean matchesSystemRule(SystemInformationRule rule,
                                      List<SystemInformationRuleCondition> conditions,
                                      ParsedPosture parsed) {
        if (conditions.isEmpty()) {
            return true;
        }
        Map<Short, List<SystemInformationRuleCondition>> grouped = new HashMap<>();
        for (SystemInformationRuleCondition c : conditions) {
            short group = c.getConditionGroup() == null ? 1 : c.getConditionGroup();
            grouped.computeIfAbsent(group, _ -> new ArrayList<>()).add(c);
        }
        List<Boolean> groupResults = new ArrayList<>();
        for (List<SystemInformationRuleCondition> group : grouped.values()) {
            boolean all = true;
            for (SystemInformationRuleCondition c : group) {
                if (!evaluateCondition(c, parsed)) {
                    all = false;
                    break;
                }
            }
            groupResults.add(all);
        }
        String mode = normalizeUpper(rule.getMatchMode());
        if ("ANY".equals(mode)) {
            return groupResults.stream().anyMatch(Boolean::booleanValue);
        }
        return groupResults.stream().allMatch(Boolean::booleanValue);
    }

    private boolean evaluateCondition(SystemInformationRuleCondition condition, ParsedPosture parsed) {
        String field = trimToNull(condition.getFieldName());
        String operator = normalizeUpper(condition.getOperator());
        if (field == null || operator == null) {
            return false;
        }
        Object actual = extractFieldValue(field, parsed);
        Object expected = expectedValue(condition);
        return switch (operator) {
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "EQ" -> compareValues(actual, expected) == 0;
            case "NEQ" -> compareValues(actual, expected) != 0;
            case "GT" -> compareValues(actual, expected) > 0;
            case "GTE" -> compareValues(actual, expected) >= 0;
            case "LT" -> compareValues(actual, expected) < 0;
            case "LTE" -> compareValues(actual, expected) <= 0;
            case "IN" -> inOperator(actual, expected, true);
            case "NOT_IN" -> inOperator(actual, expected, false);
            case "REGEX" -> regexMatch(actual, expected);
            default -> false;
        };
    }

    private Object expectedValue(SystemInformationRuleCondition c) {
        if (c.getValueNumeric() != null) {
            return c.getValueNumeric();
        }
        if (c.getValueBoolean() != null) {
            return c.getValueBoolean();
        }
        if (c.getValueJson() != null && !c.getValueJson().isBlank()) {
            try {
                JsonNode json = objectMapper.readTree(c.getValueJson());
                if (json.isArray()) {
                    List<String> values = new ArrayList<>();
                    json.forEach(x -> values.add(stringifyNode(x)));
                    return values;
                }
                return stringifyNode(json);
            } catch (Exception _) {}
        }
        return c.getValueText();
    }

    private Object extractFieldValue(String field, ParsedPosture parsed) {
        String key = field.toLowerCase(Locale.ROOT);
        Map<String, Object> fixed = new HashMap<>();
        fixed.put("tenant_id", parsed.tenantId());
        fixed.put("device_external_id", parsed.deviceExternalId());
        fixed.put("agent_id", parsed.agentId());
        fixed.put("device_type", parsed.deviceType());
        fixed.put("os_type", parsed.osType());
        fixed.put("os_name", parsed.osName());
        fixed.put("os_version", parsed.osVersion());
        fixed.put("os_cycle", parsed.osCycle());
        fixed.put("time_zone", parsed.timeZone());
        fixed.put("kernel_version", parsed.kernelVersion());
        fixed.put("api_level", parsed.apiLevel());
        fixed.put("os_build_number", parsed.osBuildNumber());
        fixed.put("manufacturer", parsed.manufacturer());
        fixed.put("root_detected", parsed.rootDetected());
        fixed.put("running_on_emulator", parsed.runningOnEmulator());
        fixed.put("usb_debugging_status", parsed.usbDebuggingStatus());
        fixed.put("installed_app_count", parsed.installedApps().size());
        if (fixed.containsKey(key)) {
            return fixed.get(key);
        }
        JsonNode root = parsed.root();
        JsonNode node;
        if (field.contains(".")) {
            node = root;
            for (String part : field.split("\\.")) {
                if (node == null) {
                    break;
                }
                node = node.get(part);
            }
        } else {
            node = root.get(field);
            if (node == null) {
                node = root.get(key);
            }
        }
        if (node == null || node.isNull()) {
            return null;
        }
        return switch (node.getNodeType()) {
            case BOOLEAN -> node.booleanValue();
            case NUMBER -> node.numberValue();
            case STRING -> node.asText();
            default -> node.toString();
        };
    }

    private boolean inOperator(Object actual, Object expected, boolean positive) {
        if (actual == null) {
            return !positive;
        }
        Collection<String> values = expectedCollection(expected);
        boolean contains = values.stream().anyMatch(v -> equalsIgnoreCase(v, String.valueOf(actual)));
        return positive == contains;
    }

    private boolean regexMatch(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        try {
            return Pattern.compile(String.valueOf(expected)).matcher(String.valueOf(actual)).find();
        } catch (Exception _) {
            return false;
        }
    }

    private int compareValues(Object actual, Object expected) {
        if (actual == null && expected == null) {
            return 0;
        }
        if (actual == null) {
            return -1;
        }
        if (expected == null) {
            return 1;
        }

        Double actualNum = toDouble(actual);
        Double expectedNum = toDouble(expected);
        if (actualNum != null && expectedNum != null) {
            return Double.compare(actualNum, expectedNum);
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            boolean a = Boolean.parseBoolean(String.valueOf(actual));
            boolean b = Boolean.parseBoolean(String.valueOf(expected));
            return Boolean.compare(a, b);
        }
        return String.valueOf(actual).compareToIgnoreCase(String.valueOf(expected));
    }

    private AppliedPolicy findAppliedPolicy(List<TrustScorePolicy> policies,
                                            String sourceType,
                                            List<String> signalCandidates,
                                            Short severity,
                                            String complianceAction,
                                            String tenantId) {
        List<TrustScorePolicy> matches = policies.stream()
                .filter(x -> equalsIgnoreCase(x.getSourceType(), sourceType))
                .filter(x -> signalCandidates.stream().anyMatch(sig -> sig != null && equalsIgnoreCase(x.getSignalKey(), sig)))
                .filter(x -> x.getSeverity() == null || Objects.equals(x.getSeverity(), severity))
                .filter(x -> x.getComplianceAction() == null || x.getComplianceAction().isBlank()
                        || equalsIgnoreCase(x.getComplianceAction(), complianceAction))
                .sorted(Comparator
                        .comparingInt((TrustScorePolicy x) -> scopePriority(x.getTenantId(), tenantId))
                        .thenComparing((TrustScorePolicy x) -> x.getWeight() == null ? 1.0 : x.getWeight(), Comparator.reverseOrder())
                        .thenComparing(TrustScorePolicy::getId))
                .toList();
        if (matches.isEmpty()) {
            return null;
        }
        return new AppliedPolicy(matches.getFirst());
    }

    private boolean isInPolicyScope(String policyTenantId, String tenantId) {
        String recordTenant = normalizeOptionalTenantId(policyTenantId);
        String normalizedTenant = normalizeOptionalTenantId(tenantId);
        if (normalizedTenant == null) {
            return recordTenant == null;
        }
        return recordTenant == null || Objects.equals(recordTenant, normalizedTenant);
    }

    private int scopePriority(String policyTenantId, String tenantId) {
        String recordTenant = normalizeOptionalTenantId(policyTenantId);
        String normalizedTenant = normalizeOptionalTenantId(tenantId);
        if (normalizedTenant != null && Objects.equals(recordTenant, normalizedTenant)) {
            return 0;
        }
        if (recordTenant == null) {
            return 1;
        }
        return 2;
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        String normalized = tenantId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private boolean matchesRejectApp(DeviceInstalledApplication app, RejectApplication reject) {
        if (!equalsIgnoreCase(app.getAppOsType(), reject.getAppOsType())) {
            return false;
        }
        boolean identityMatch = false;
        if (reject.getPackageId() != null && !reject.getPackageId().isBlank()) {
            identityMatch = equalsIgnoreCase(reject.getPackageId(), app.getPackageId());
        } else if (reject.getAppName() != null && !reject.getAppName().isBlank()) {
            identityMatch = equalsIgnoreCase(reject.getAppName(), app.getAppName());
        }
        if (!identityMatch) {
            return false;
        }
        if (reject.getMinAllowedVersion() != null && !reject.getMinAllowedVersion().isBlank()) {
            if (app.getAppVersion() == null || app.getAppVersion().isBlank()) {
                return true;
            }
            return compareVersion(app.getAppVersion(), reject.getMinAllowedVersion()) < 0;
        }
        return true;
    }

    private short weightedDelta(TrustScorePolicy policy) {
        double weight = policy.getWeight() == null ? 1.0 : policy.getWeight();
        int computed = (int) Math.round(policy.getScoreDelta() * weight);
        if (computed > 1000) {
            computed = 1000;
        }
        if (computed < -1000) {
            computed = -1000;
        }
        return (short) computed;
    }

    private short defaultRejectDelta(short severity) {
        return (short) Math.max(-80, -10 * severity);
    }

    private short defaultLifecycleDelta(String state) {
        return switch (normalizeUpper(state)) {
            case "EEOL" -> -40;
            case "EOL" -> -25;
            case "NOT_TRACKED" -> -15;
            case null -> (short)0;
            default -> 0;
        };
    }

    private String defaultDecisionForScore(short score) {
        if (score < 40) {
            return "BLOCK";
        }
        if (score < 60) {
            return "QUARANTINE";
        }
        if (score < 80) {
            return "NOTIFY";
        }
        return "ALLOW";
    }

    private String scoreBandFor(short score) {
        if (score < 25) {
            return "CRITICAL";
        }
        if (score < 50) {
            return "HIGH_RISK";
        }
        if (score < 70) {
            return "MEDIUM_RISK";
        }
        if (score < 90) {
            return "LOW_RISK";
        }
        return "TRUSTED";
    }

    private String postureStatusFor(String decisionAction) {
        return "ALLOW".equalsIgnoreCase(decisionAction) ? "COMPLIANT" : "NON_COMPLIANT";
    }

    private String lifecycleSignalFor(String state) {
        return switch (normalizeUpper(state)) {
            case "EEOL" -> "OS_EEOL";
            case "EOL" -> "OS_EOL";
            case "NOT_TRACKED" -> "OS_NOT_TRACKED";
            case null -> "-";
            default -> "OS_SUPPORTED";
        };
    }

    private boolean inEffectiveWindow(OffsetDateTime from, OffsetDateTime to, OffsetDateTime now) {
        if (from != null && from.isAfter(now)) {
            return false;
        }
        return to == null || to.isAfter(now);
    }

    private String deriveCycle(String osVersion) {
        String v = trimToNull(osVersion);
        if (v == null) {
            return null;
        }
        String major = v.split("[^A-Za-z0-9]+")[0];
        return trimToNull(major);
    }

    private int compareVersion(String left, String right) {
        String[] a = left.split("[._-]");
        String[] b = right.split("[._-]");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            Integer ai = parseIntOrNull(av);
            Integer bi = parseIntOrNull(bv);
            int cmp;
            if (ai != null && bi != null) {
                cmp = Integer.compare(ai, bi);
            } else {
                cmp = av.compareToIgnoreCase(bv);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private Optional<OffsetDateTime> parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value.trim()));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }

    private Optional<DeviceSystemSnapshot> loadSnapshotByPayload(Long payloadId) {
        String sql = "SELECT id FROM device_system_snapshot WHERE device_posture_payload_id = :payloadId LIMIT 1";
        List<Long> ids = jdbc.query(sql, new MapSqlParameterSource("payloadId", payloadId), (rs, _) -> rs.getLong(1));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return snapshotRepository.findById(ids.getFirst());
    }

    private List<DeviceInstalledApplication> loadInstalledAppsByPayload(Long payloadId) {
        String sql = "SELECT id FROM device_installed_application WHERE device_posture_payload_id = :payloadId";
        List<Long> ids = jdbc.query(sql, new MapSqlParameterSource("payloadId", payloadId), (rs, _) -> rs.getLong(1));
        List<DeviceInstalledApplication> out = new ArrayList<>();
        for (Long id : ids) {
            installedApplicationRepository.findById(id).ifPresent(out::add);
        }
        return out;
    }

    private boolean snapshotExists(Long payloadId) {
        String sql = "SELECT COUNT(*) FROM device_system_snapshot WHERE device_posture_payload_id = :payloadId";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("payloadId", payloadId), Long.class);
        return count != null && count > 0;
    }

    private boolean installedAppsExist(Long payloadId) {
        String sql = "SELECT COUNT(*) FROM device_installed_application WHERE device_posture_payload_id = :payloadId";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("payloadId", payloadId), Long.class);
        return count != null && count > 0;
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
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return (short) value;
    }

    private String normalizeDecision(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null || !DECISION_ACTIONS.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private String validDeviceType(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        return Set.of("PHONE", "TABLET", "LAPTOP", "DESKTOP", "IOT", "SERVER")
                .contains(normalized) ? normalized : null;
    }

    private <T> List<T> toList(Iterable<T> iterable) {
        if (iterable == null) {
            return Collections.emptyList();
        }
        List<T> out = new ArrayList<>();
        iterable.forEach(out::add);
        return out;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON serialization failed");
        }
    }

}
