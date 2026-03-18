package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.domain.DeviceTrustScoreEvent;
import com.e24online.mdm.domain.PostureEvaluationRun;
import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.DeviceTrustScoreEventRepository;
import com.e24online.mdm.repository.PostureEvaluationRunRepository;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.web.dto.DeviceTimelineEvent;
import com.e24online.mdm.web.dto.DeviceTimelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for building device history timeline.
 * Aggregates trust score events, evaluation runs, and decisions
 * into a unified timeline view.
 */
@Service
public class DeviceTimelineService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTimelineService.class);

    private final DeviceTrustScoreEventRepository scoreEventRepository;
    private final PostureEvaluationRunRepository evaluationRunRepository;
    private final DeviceDecisionResponseRepository decisionResponseRepository;
    private final DeviceTrustProfileRepository trustProfileRepository;
    private final SystemInformationRuleRepository systemRuleRepository;
    private final RejectApplicationRepository rejectAppRepository;

    public DeviceTimelineService(
            DeviceTrustScoreEventRepository scoreEventRepository,
            PostureEvaluationRunRepository evaluationRunRepository,
            DeviceDecisionResponseRepository decisionResponseRepository,
            DeviceTrustProfileRepository trustProfileRepository,
            SystemInformationRuleRepository systemRuleRepository,
            RejectApplicationRepository rejectAppRepository) {
        this.scoreEventRepository = scoreEventRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.decisionResponseRepository = decisionResponseRepository;
        this.trustProfileRepository = trustProfileRepository;
        this.systemRuleRepository = systemRuleRepository;
        this.rejectAppRepository = rejectAppRepository;
    }

    /**
     * Get device history timeline.
     *
     * @param deviceTrustProfileId Device trust profile ID
     * @param limit                Maximum number of events to return
     * @return Timeline response with aggregated events
     */
    public DeviceTimelineResponse getTimeline(Long deviceTrustProfileId, int limit) {
        Optional<DeviceTrustProfile> profileOpt = trustProfileRepository.findById(deviceTrustProfileId);
        if (profileOpt.isEmpty()) {
            throw new IllegalArgumentException("Device trust profile not found: " + deviceTrustProfileId);
        }

        DeviceTrustProfile profile = profileOpt.get();

        List<DeviceTrustScoreEvent> scoreEvents = scoreEventRepository
                .findByDeviceTrustProfileIdOrderByEventTimeDesc(deviceTrustProfileId);

        List<PostureEvaluationRun> evaluationRuns = evaluationRunRepository
                .findByDeviceTrustProfileIdOrderByCreatedAtDesc(deviceTrustProfileId);

        List<DeviceDecisionResponse> decisionResponses = decisionResponseRepository
                .findByDeviceTrustProfileIdOrderByCreatedAtDesc(deviceTrustProfileId);

        List<DeviceTimelineEvent> timelineEvents = new ArrayList<>(
                scoreEvents.size() + evaluationRuns.size() + decisionResponses.size()
        );

        for (DeviceTrustScoreEvent event : scoreEvents) {
            timelineEvents.add(toScoreTimelineEvent(event));
        }

        for (PostureEvaluationRun run : evaluationRuns) {
            timelineEvents.add(toEvaluationTimelineEvent(run));
        }

        for (DeviceDecisionResponse response : decisionResponses) {
            timelineEvents.add(toDecisionTimelineEvent(response));
        }

        timelineEvents.sort(
                Comparator.comparing(
                        DeviceTimelineEvent::timestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed()
        );

        long totalEvents = timelineEvents.size();

        List<DeviceTimelineEvent> limitedEvents = timelineEvents;
        if (limit > 0 && timelineEvents.size() > limit) {
            limitedEvents = new ArrayList<>(timelineEvents.subList(0, limit));
        }

        OffsetDateTime startTime = limitedEvents.stream()
                .map(DeviceTimelineEvent::timestamp)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        OffsetDateTime endTime = limitedEvents.stream()
                .map(DeviceTimelineEvent::timestamp)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String currentDecision = resolveCurrentDecision(decisionResponses, evaluationRuns);

        return new DeviceTimelineResponse(
                profile.getDeviceExternalId(),
                profile.getDeviceType(),
                profile.getOsType(),
                profile.getCurrentScore() != null ? profile.getCurrentScore().intValue() : null,
                profile.getScoreBand(),
                currentDecision,
                limitedEvents,
                totalEvents,
                startTime,
                endTime
        );
    }

    private DeviceTimelineEvent toScoreTimelineEvent(DeviceTrustScoreEvent event) {
        String title = "Trust Score Changed";
        String description = buildScoreEventDescription(event);
        DeviceTimelineEvent.EventCategory category = DeviceTimelineEvent.EventCategory.SCORE;
        DeviceTimelineEvent.Severity severity = calculateScoreSeverity(event.getScoreDelta());

        String ruleName = null;
        if (event.getSystemInformationRuleId() != null) {
            ruleName = getRuleName(event.getSystemInformationRuleId());
        } else if (event.getRejectApplicationListId() != null) {
            ruleName = getAppName(event.getRejectApplicationListId());
        }

        OffsetDateTime timestamp = event.getEventTime() != null ? event.getEventTime() : event.getProcessedAt();

        return new DeviceTimelineEvent(
                event.getId(),
                timestamp,
                DeviceTimelineEvent.EventType.SCORE_CHANGE,
                event.getScoreBefore() != null ? event.getScoreBefore().intValue() : null,
                event.getScoreAfter() != null ? event.getScoreAfter().intValue() : null,
                event.getScoreDelta() != null ? event.getScoreDelta().intValue() : null,
                null,
                title,
                description,
                category,
                severity,
                ruleName,
                null,
                null
        );
    }

    private DeviceTimelineEvent toEvaluationTimelineEvent(PostureEvaluationRun run) {
        int matchedRuleCount = run.getMatchedRuleCount() != null ? run.getMatchedRuleCount() : 0;
        int matchedAppCount = run.getMatchedAppCount() != null ? run.getMatchedAppCount() : 0;
        int scoreBefore = run.getTrustScoreBefore() != null ? run.getTrustScoreBefore() : 0;
        int scoreAfter = run.getTrustScoreAfter() != null ? run.getTrustScoreAfter() : 0;
        int scoreDelta = run.getTrustScoreDeltaTotal() != null ? run.getTrustScoreDeltaTotal() : 0;

        String title = "Posture Evaluation";
        String description = String.format(
                "Evaluation completed: %d rules matched, %d apps matched. Score: %d -> %d (delta %s%d)",
                matchedRuleCount,
                matchedAppCount,
                scoreBefore,
                scoreAfter,
                scoreDelta > 0 ? "+" : "",
                scoreDelta
        );

        DeviceTimelineEvent.Severity severity = DeviceTimelineEvent.Severity.INFO;
        if ("FAILED".equalsIgnoreCase(run.getEvaluationStatus())) {
            severity = DeviceTimelineEvent.Severity.CRITICAL;
        } else if (scoreDelta < -30) {
            severity = DeviceTimelineEvent.Severity.WARNING;
        }

        OffsetDateTime timestamp = run.getEvaluatedAt() != null ? run.getEvaluatedAt() : run.getCreatedAt();

        return new DeviceTimelineEvent(
                run.getId(),
                timestamp,
                DeviceTimelineEvent.EventType.EVALUATION,
                run.getTrustScoreBefore() != null ? run.getTrustScoreBefore().intValue() : null,
                run.getTrustScoreAfter() != null ? run.getTrustScoreAfter().intValue() : null,
                run.getTrustScoreDeltaTotal() != null ? run.getTrustScoreDeltaTotal().intValue() : null,
                run.getDecisionAction(),
                title,
                description,
                DeviceTimelineEvent.EventCategory.SECURITY,
                severity,
                null,
                run.isRemediationRequired(),
                null
        );
    }

    private DeviceTimelineEvent toDecisionTimelineEvent(DeviceDecisionResponse response) {
        String action = hasText(response.getDecisionAction()) ? response.getDecisionAction() : "UNKNOWN";
        int trustScore = response.getTrustScore() != null ? response.getTrustScore() : 0;

        StringBuilder description = new StringBuilder();
        description.append("Device decision: ")
                .append(action)
                .append(" with trust score ")
                .append(trustScore);

        if (hasText(response.getDeliveryStatus())) {
            description.append(" (delivery: ").append(response.getDeliveryStatus()).append(")");
        }

        if (response.isRemediationRequired()) {
            description.append(". Remediation required.");
        }

        DeviceTimelineEvent.Severity severity = switch (action) {
            case "BLOCK" -> DeviceTimelineEvent.Severity.CRITICAL;
            case "QUARANTINE" -> DeviceTimelineEvent.Severity.WARNING;
            case "NOTIFY" -> DeviceTimelineEvent.Severity.INFO;
            default -> DeviceTimelineEvent.Severity.INFO;
        };

        OffsetDateTime timestamp = response.getCreatedAt() != null ? response.getCreatedAt() : response.getSentAt();

        return new DeviceTimelineEvent(
                response.getId(),
                timestamp,
                DeviceTimelineEvent.EventType.DECISION,
                null,
                null,
                null,
                response.getDecisionAction(),
                "Decision: " + action,
                description.toString(),
                DeviceTimelineEvent.EventCategory.DECISION,
                severity,
                null,
                response.isRemediationRequired(),
                null
        );
    }

    private String buildScoreEventDescription(DeviceTrustScoreEvent event) {
        StringBuilder sb = new StringBuilder();

        if (event.getScoreBefore() != null && event.getScoreAfter() != null) {
            sb.append("Score changed from ")
                    .append(event.getScoreBefore())
                    .append(" to ")
                    .append(event.getScoreAfter());

            if (event.getScoreDelta() != null) {
                sb.append(" (")
                        .append(event.getScoreDelta() > 0 ? "+" : "")
                        .append(event.getScoreDelta())
                        .append(")");
            }
        }

        if (event.getEventSource() != null) {
            sb.append(" - Source: ").append(event.getEventSource());
        }

        if (event.getOsLifecycleState() != null) {
            sb.append(" - OS Lifecycle: ").append(event.getOsLifecycleState());
        }

        if (event.getSourceRecordId() != null) {
            sb.append(" - Source Record: ").append(event.getSourceRecordId());
        }

        if (hasText(event.getNotes())) {
            sb.append(" - Notes: ").append(event.getNotes());
        }

        return sb.toString();
    }

    private DeviceTimelineEvent.Severity calculateScoreSeverity(Short scoreDelta) {
        if (scoreDelta == null) {
            return DeviceTimelineEvent.Severity.INFO;
        }

        if (scoreDelta >= 0) {
            return DeviceTimelineEvent.Severity.INFO;
        } else if (scoreDelta >= -20) {
            return DeviceTimelineEvent.Severity.WARNING;
        } else {
            return DeviceTimelineEvent.Severity.CRITICAL;
        }
    }

    private String resolveCurrentDecision(
            List<DeviceDecisionResponse> decisionResponses,
            List<PostureEvaluationRun> evaluationRuns
    ) {
        for (DeviceDecisionResponse response : decisionResponses) {
            if (hasText(response.getDecisionAction())) {
                return response.getDecisionAction();
            }
        }

        for (PostureEvaluationRun run : evaluationRuns) {
            if (hasText(run.getDecisionAction())) {
                return run.getDecisionAction();
            }
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String getRuleName(Long ruleId) {
        try {
            return systemRuleRepository.findById(ruleId)
                    .map(SystemInformationRule::getRuleCode)
                    .orElse("Unknown Rule");
        } catch (Exception e) {
            log.debug("Failed to get rule name for ID: {}", ruleId, e);
            return "Rule #" + ruleId;
        }
    }

    private String getAppName(Long appId) {
        try {
            return rejectAppRepository.findById(appId)
                    .map(RejectApplication::getAppName)
                    .orElse("Unknown App");
        } catch (Exception e) {
            log.debug("Failed to get app name for ID: {}", appId, e);
            return "App #" + appId;
        }
    }
}
