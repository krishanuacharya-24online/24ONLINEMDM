package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import com.e24online.mdm.web.dto.SimplePolicySimulationAppInput;
import com.e24online.mdm.web.dto.SimplePolicySimulationFinding;
import com.e24online.mdm.web.dto.SimplePolicySimulationRequest;
import com.e24online.mdm.web.dto.SimplePolicySimulationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SimplePolicySimulationService {

    private final DeviceStateService deviceStateService;
    private final EvaluationEngineService evaluationEngineService;
    private final BlockingDb blockingDb;
    private final ObjectMapper objectMapper;

    public SimplePolicySimulationService(DeviceStateService deviceStateService,
                                         EvaluationEngineService evaluationEngineService,
                                         BlockingDb blockingDb,
                                         ObjectMapper objectMapper) {
        this.deviceStateService = deviceStateService;
        this.evaluationEngineService = evaluationEngineService;
        this.blockingDb = blockingDb;
        this.objectMapper = objectMapper;
    }

    public Mono<SimplePolicySimulationResponse> simulate(
            String tenantId,
            Mono<SimplePolicySimulationRequest> request
    ) {
        return request
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required")
                ))
                .flatMap(body -> blockingDb.mono(() -> simulateInternal(tenantId, body)));
    }

    private SimplePolicySimulationResponse simulateInternal(
            String tenantId,
            SimplePolicySimulationRequest body
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        ObjectNode root = buildRoot(body);

        ParsedPosture parsed = deviceStateService.parsePosture(
                root,
                defaultIfBlank(body.getDeviceExternalId(), "simulated-device"),
                "simple-policy-simulator",
                tenantId,
                now
        );

        LifecycleResolution lifecycle = deviceStateService.resolveLifecycle(parsed, now.toLocalDate());

        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setTenantId(tenantId);
        profile.setDeviceExternalId(parsed.deviceExternalId());
        profile.setCurrentScore(clampScore(body.getCurrentScore()));

        EvaluationComputation computation = evaluationEngineService.computeEvaluation(
                profile,
                parsed,
                toInstalledApps(body.getInstalledApps(), parsed, now),
                lifecycle,
                now
        );

        return toResponse(computation, lifecycle);
    }

    private ObjectNode buildRoot(SimplePolicySimulationRequest body) {
        ObjectNode root = objectMapper.createObjectNode();
        putText(root, "os_type", body.getOsType());
        putText(root, "os_name", body.getOsName());
        putText(root, "os_version", body.getOsVersion());
        putText(root, "os_cycle", body.getOsCycle());
        putText(root, "device_type", body.getDeviceType());
        putText(root, "time_zone", body.getTimeZone());
        putText(root, "kernel_version", body.getKernelVersion());
        putText(root, "os_build_number", body.getOsBuildNumber());
        putText(root, "manufacturer", body.getManufacturer());
        if (body.getApiLevel() != null) {
            root.put("api_level", body.getApiLevel());
        }
        if (body.getRootDetected() != null) {
            root.put("root_detected", body.getRootDetected());
        }
        if (body.getRunningOnEmulator() != null) {
            root.put("running_on_emulator", body.getRunningOnEmulator());
        }
        if (body.getUsbDebuggingStatus() != null) {
            root.put("usb_debugging_status", body.getUsbDebuggingStatus());
        }

        ArrayNode installedApps = objectMapper.createArrayNode();
        for (SimplePolicySimulationAppInput app : body.getInstalledApps()) {
            if (app == null) {
                continue;
            }
            String appName = trimToNull(app.getAppName());
            String packageId = trimToNull(app.getPackageId());
            if (appName == null && packageId == null) {
                continue;
            }
            ObjectNode appNode = objectMapper.createObjectNode();
            putText(appNode, "app_name", appName == null ? packageId : appName);
            putText(appNode, "package_id", packageId);
            putText(appNode, "app_version", app.getAppVersion());
            putText(appNode, "publisher", app.getPublisher());
            putText(appNode, "app_os_type", defaultIfBlank(app.getAppOsType(), body.getOsType()));
            installedApps.add(appNode);
        }
        root.set("installed_apps", installedApps);
        return root;
    }

    private List<DeviceInstalledApplication> toInstalledApps(List<SimplePolicySimulationAppInput> rows,
                                                             ParsedPosture parsed,
                                                             OffsetDateTime now) {
        List<DeviceInstalledApplication> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (SimplePolicySimulationAppInput row : rows) {
            if (row == null) {
                continue;
            }
            String appName = trimToNull(row.getAppName());
            String packageId = trimToNull(row.getPackageId());
            if (appName == null && packageId == null) {
                continue;
            }
            DeviceInstalledApplication app = new DeviceInstalledApplication();
            app.setCaptureTime(now);
            app.setAppName(appName == null ? packageId : appName);
            app.setPackageId(packageId);
            app.setAppVersion(trimToNull(row.getAppVersion()));
            app.setPublisher(trimToNull(row.getPublisher()));
            app.setAppOsType(defaultIfBlank(trimToNull(row.getAppOsType()), parsed.osType()));
            app.setStatus("ACTIVE");
            app.setCreatedAt(now);
            app.setCreatedBy("simple-policy-simulator");
            out.add(app);
        }
        return out;
    }

    private SimplePolicySimulationResponse toResponse(EvaluationComputation computation,
                                                      LifecycleResolution lifecycle) {
        SimplePolicySimulationResponse response = new SimplePolicySimulationResponse();
        response.setScoreBefore(computation.scoreBefore());
        response.setScoreAfter(computation.scoreAfter());
        response.setScoreDeltaTotal(computation.scoreDeltaTotal());
        response.setMatchedRuleCount(computation.matchedRuleCount());
        response.setMatchedAppCount(computation.matchedAppCount());
        response.setDecisionAction(computation.decisionAction());
        response.setDecisionReason(computation.decisionReason());
        response.setRemediationRequired(computation.remediationRequired());
        response.setLifecycleState(lifecycle.state());
        response.setLifecycleSignalKey(lifecycle.signalKey());

        List<SimplePolicySimulationFinding> findings = new ArrayList<>();
        boolean hasLifecycleMatch = false;
        for (MatchDraft match : computation.matches()) {
            SimplePolicySimulationFinding finding = toFinding(match);
            findings.add(finding);
            if ("LIFECYCLE".equals(finding.getCategory())) {
                hasLifecycleMatch = true;
            }
        }
        if (!hasLifecycleMatch) {
            for (ScoreSignal signal : computation.scoreSignals()) {
                if (!"POSTURE_SIGNAL".equalsIgnoreCase(signal.eventSource())) {
                    continue;
                }
                findings.add(toFinding(signal));
            }
        }
        response.setFindings(findings);
        return response;
    }

    private SimplePolicySimulationFinding toFinding(MatchDraft match) {
        SimplePolicySimulationFinding finding = new SimplePolicySimulationFinding();
        finding.setSeverity(match.severity());
        finding.setAction(match.complianceAction());
        finding.setScoreDelta(match.scoreDelta());

        String source = normalizeUpper(match.matchSource());
        if ("SYSTEM_RULE".equals(source)) {
            finding.setCategory("DEVICE_CHECK");
            String ruleTag = readJsonField(match.matchDetail(), "rule_tag");
            String ruleCode = readJsonField(match.matchDetail(), "rule_code");
            finding.setTitle(defaultIfBlank(ruleTag, defaultIfBlank(ruleCode, "Device check")));
            finding.setDetail("Matched device posture rule.");
            return finding;
        }
        if ("REJECT_APPLICATION".equals(source)) {
            finding.setCategory("APP_RULE");
            String appName = readJsonField(match.matchDetail(), "app_name");
            String packageId = readJsonField(match.matchDetail(), "package_id");
            finding.setTitle(defaultIfBlank(appName, defaultIfBlank(packageId, "App rule")));
            finding.setDetail("Matched blocked app or unsupported app version.");
            return finding;
        }
        if ("TRUST_POLICY".equals(source)) {
            finding.setCategory("LIFECYCLE");
            String signalKey = readJsonField(match.matchDetail(), "signal_key");
            finding.setTitle(defaultIfBlank(signalKey, "Lifecycle signal"));
            finding.setDetail("Lifecycle signal changed the trust score.");
            return finding;
        }

        finding.setCategory(defaultIfBlank(source, "OTHER"));
        finding.setTitle(defaultIfBlank(source, "Finding"));
        finding.setDetail("Matched policy finding.");
        return finding;
    }

    private SimplePolicySimulationFinding toFinding(ScoreSignal signal) {
        SimplePolicySimulationFinding finding = new SimplePolicySimulationFinding();
        finding.setCategory("LIFECYCLE");
        finding.setTitle(defaultIfBlank(signal.osLifecycleState(), "Lifecycle signal"));
        finding.setDetail(defaultIfBlank(signal.notes(), "Lifecycle state affected the trust score."));
        finding.setScoreDelta(signal.scoreDelta());
        return finding;
    }

    private String readJsonField(String rawJson, String key) {
        String normalizedKey = trimToNull(key);
        if (normalizedKey == null || trimToNull(rawJson) == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode node = root.get(normalizedKey);
            if (node == null || node.isNull()) {
                return null;
            }
            return trimToNull(node.asText());
        } catch (Exception _) {
            return null;
        }
    }

    private void putText(ObjectNode root, String field, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            root.put(field, normalized);
        }
    }

    private Short clampScore(Integer score) {
        int safe = score == null ? 100 : score;
        if (safe < 0) {
            safe = 0;
        }
        if (safe > 100) {
            safe = 100;
        }
        return (short) safe;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalizeUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
