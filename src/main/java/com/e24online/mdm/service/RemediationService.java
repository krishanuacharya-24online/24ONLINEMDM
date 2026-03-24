package com.e24online.mdm.service;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.records.posture.evaluation.*;
import com.e24online.mdm.records.remediation.PriorOpenRemediation;
import com.e24online.mdm.records.remediation.RemediationRescanKey;
import com.e24online.mdm.repository.PostureEvaluationMatchRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import com.e24online.mdm.web.dto.RemediationSummary;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.e24online.mdm.utils.AgentWorkflowValueUtils.*;
import static com.e24online.mdm.utils.WorkflowStatusModel.canonicalRemediationStatus;

/**
 * Service for managing remediation rules and generating remediation actions.
 */
@Service
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);

    private final RuleRemediationMappingRepository ruleRemediationMappingRepository;
    private final RemediationRuleRepository remediationRuleRepository;
    private final PostureEvaluationRemediationRepository remediationRepository;
    private final PostureEvaluationMatchRepository matchRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Scheduler jdbcScheduler;

    public RemediationService(RuleRemediationMappingRepository ruleRemediationMappingRepository,
                              RemediationRuleRepository remediationRuleRepository,
                              PostureEvaluationRemediationRepository remediationRepository,
                              PostureEvaluationMatchRepository matchRepository,
                              NamedParameterJdbcTemplate jdbc,
                              ObjectMapper objectMapper,
                              Scheduler jdbcScheduler) {
        this.ruleRemediationMappingRepository = ruleRemediationMappingRepository;
        this.remediationRuleRepository = remediationRuleRepository;
        this.remediationRepository = remediationRepository;
        this.matchRepository = matchRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.jdbcScheduler = jdbcScheduler;
    }

    /**
     * Save remediation actions based on evaluation matches and decision.
     */
    public List<SavedRemediation> saveRemediation(PostureEvaluationRun run,
                                                  List<SavedMatch> matches,
                                                  ParsedPosture posture,
                                                  OffsetDateTime now) {
        log.debug("Saving remediation for {} postures", matches.size());
        List<RuleRemediationMapping> mappings = activeMappings(posture.tenantId(), now);
        Map<Long, RemediationRule> remediationById = activeRemediationRules(posture.tenantId(), now).stream()
                .collect(Collectors.toMap(RemediationRule::getId, x -> x));

        List<RemediationCandidate> candidates = new ArrayList<>();

        // Add remediations from matched rules
        for (SavedMatch savedMatch : matches) {
            MatchDraft draft = savedMatch.draft();
            List<RuleRemediationMapping> mapped = mappingsForMatch(mappings, draft);
            for (RuleRemediationMapping mapping : mapped) {
                candidates.add(new RemediationCandidate(mapping, savedMatch.match().getId(), "MATCH"));
            }
        }

        // Add remediations from decision action
        for (RuleRemediationMapping mapping : mappings) {
            boolean isDecisionSource = "DECISION".equalsIgnoreCase(mapping.getSourceType());
            boolean isMatchingDecisionAction = equalsIgnoreCase(
                    mapping.getDecisionAction(),
                    run.getDecisionAction()
            );

            if (isDecisionSource && isMatchingDecisionAction) {
                candidates.add(new RemediationCandidate(mapping, null, "DECISION"));
            }
        }

        // Sort by rank order
        candidates.sort(Comparator.comparingInt(x -> x.mapping().getRankOrder() == null ? Integer.MAX_VALUE : x.mapping().getRankOrder()));

        Set<String> dedupe = new HashSet<>();
        List<SavedRemediation> saved = new ArrayList<>();

        for (RemediationCandidate candidate : candidates) {
            RuleRemediationMapping mapping = candidate.mapping();
            Long remediationRuleId = mapping.getRemediationRuleId();
            RemediationRule rule = remediationById.get(remediationRuleId);

            String dedupeKey = run.getId() + "|" + remediationRuleId + "|" + candidate.matchId();
            boolean shouldPersist =
                    matchesRemediationTarget(rule, posture, now)
                            && dedupe.add(dedupeKey);

            if (shouldPersist) {
                PostureEvaluationRemediation remediation = new PostureEvaluationRemediation();
                remediation.setPostureEvaluationRunId(run.getId());
                remediation.setRemediationRuleId(remediationRuleId);
                remediation.setPostureEvaluationMatchId(candidate.matchId());
                remediation.setSourceType(candidate.sourceType());
                remediation.setRemediationStatus("PROPOSED");
                remediation.setInstructionOverride(rule.getInstructionJson());
                remediation.setCreatedAt(now);
                remediation.setCreatedBy("rule-engine");

                PostureEvaluationRemediation persistedRemediation = remediationRepository.save(remediation);
                saved.add(new SavedRemediation(persistedRemediation, rule, mapping.getEnforceMode()));
            }
        }

        return saved;
    }

    public List<RemediationStatusTransition> reconcilePriorOpenRemediations(PostureEvaluationRun currentRun,
                                                                            List<SavedMatch> currentMatches,
                                                                            List<SavedRemediation> currentRemediation,
                                                                            OffsetDateTime verifiedAt) {
        if (currentRun == null || currentRun.getId() == null || currentRun.getDeviceTrustProfileId() == null) {
            return List.of();
        }

        Map<Long, MatchDraft> currentMatchDraftsById = getLongMatchDraftMap(currentMatches);

        Set<RemediationRescanKey> currentKeys = new HashSet<>();
        for (SavedRemediation savedRemediation : currentRemediation == null ? List.<SavedRemediation>of() : currentRemediation) {
            RemediationRescanKey key = currentKey(savedRemediation, currentMatchDraftsById);
            if (key != null) {
                currentKeys.add(key);
            }
        }

        List<PriorOpenRemediation> priorOpenRemediation = loadPriorOpenRemediation(
                currentRun.getDeviceTrustProfileId(),
                currentRun.getId()
        );
        if (priorOpenRemediation.isEmpty()) {
            return List.of();
        }

        List<RemediationStatusTransition> transitions = new ArrayList<>();
        for (PriorOpenRemediation prior : priorOpenRemediation) {
            String fromStatus = canonicalRemediationStatus(prior.remediationStatus());
            RemediationRescanKey priorKey = priorKey(prior);
            boolean stillOpen = currentKeys.contains(priorKey);
            String toStatus = stillOpen ? "STILL_OPEN" : "RESOLVED_ON_RESCAN";
            OffsetDateTime completedAt = stillOpen ? null : verifiedAt;

            if (Objects.equals(fromStatus, toStatus) && Objects.equals(prior.completedAt(), completedAt)) {
                continue;
            }

            transitions.add(new RemediationStatusTransition(
                    prior.id(),
                    prior.postureEvaluationRunId(),
                    prior.remediationRuleId(),
                    prior.sourceType(),
                    prior.matchSource(),
                    fromStatus,
                    toStatus,
                    completedAt
            ));
        }

        if (transitions.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource[] batch = transitions.stream()
                .map(transition -> new MapSqlParameterSource()
                        .addValue("id", transition.remediationId())
                        .addValue("status", transition.toStatus())
                        .addValue("completedAt", transition.completedAt()))
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate("""
                UPDATE posture_evaluation_remediation
                   SET remediation_status = :status,
                       completed_at = :completedAt
                 WHERE id = :id
                """, batch);

        log.debug("Reconciled {} prior remediation rows for runId={}", transitions.size(), currentRun.getId());
        return transitions;
    }

    private static @NonNull Map<Long, MatchDraft> getLongMatchDraftMap(List<SavedMatch> currentMatches) {
        Map<Long, MatchDraft> currentMatchDraftsById = new HashMap<>();

        List<SavedMatch> matches = currentMatches != null ? currentMatches : List.of();

        for (SavedMatch savedMatch : matches) {
            if (savedMatch != null) {
                var match = savedMatch.match();
                var draft = savedMatch.draft();

                if (match != null && draft != null) {
                    Long matchId = match.getId();
                    if (matchId != null) {
                        currentMatchDraftsById.put(matchId, draft);
                    }
                }
            }
        }
        return currentMatchDraftsById;
    }

    public int markDelivered(Long runId) {
        if (runId == null || runId <= 0L) {
            return 0;
        }
        return remediationRepository.markDeliveredByRunId(runId);
    }

    public int markAcknowledged(Long runId, OffsetDateTime completedAt) {
        if (runId == null || runId <= 0L) {
            return 0;
        }
        OffsetDateTime effectiveCompletedAt = completedAt != null ? completedAt : OffsetDateTime.now();
        return remediationRepository.markAcknowledgedByRunId(runId, effectiveCompletedAt);
    }

    /**
     * Build decision response payload.
     */
    public String buildDecisionPayload(PostureEvaluationRun run, List<SavedRemediation> remediation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluation_run_id", run.getId());
        payload.put("decision_action", run.getDecisionAction());
        payload.put("trust_score", run.getTrustScoreAfter());
        payload.put("decision_reason", run.getDecisionReason());
        payload.put("remediation_required", run.isRemediationRequired());
        payload.put("remediation", toRemediationPayload(remediation));
        return toJson(payload);
    }

    /**
     * Build response DTO from evaluation results.
     */
    public PosturePayloadIngestResponse buildResponse(
            DevicePosturePayload payload,
            PostureEvaluationRun run,
            DeviceDecisionResponse decision,
            List<SavedRemediation> remediation) {

        return new PosturePayloadIngestResponse(
                payload.getId(),
                payload.getProcessStatus(),
                run.getId(),
                decision.getId(),
                decision.getDecisionAction(),
                decision.getTrustScore(),
                run.getDecisionReason(),
                run.isRemediationRequired(),
                remediation.stream().map(this::toRemediationSummary).toList()
        );
    }

    private List<RuleRemediationMapping> activeMappings(String tenantId, OffsetDateTime now) {
        return ruleRemediationMappingRepository.findActiveForEvaluation(tenantId, now);
    }

    private List<RemediationRule> activeRemediationRules(String tenantId, OffsetDateTime now) {
        return remediationRuleRepository.findActiveForEvaluation(tenantId, now);
    }

    private List<RuleRemediationMapping> mappingsForMatch(List<RuleRemediationMapping> mappings, MatchDraft draft) {
        List<RuleRemediationMapping> out = new ArrayList<>();

        for (RuleRemediationMapping mapping : mappings) {
            String source = normalizeUpper(mapping.getSourceType());

            addIfMatches(draft, mapping, source, out);
        }

        return out;
    }

    private static void addIfMatches(
            MatchDraft draft,
            RuleRemediationMapping mapping,
            String source,
            List<RuleRemediationMapping> out
    ) {
        if (!Objects.equals(source, draft.matchSource())) {
            return;
        }
        boolean matches = switch (source) {
            case "SYSTEM_RULE" -> Objects.equals(mapping.getSystemInformationRuleId(), draft.systemRuleId());
            case "REJECT_APPLICATION" -> Objects.equals(mapping.getRejectApplicationListId(), draft.rejectApplicationId());
            case "TRUST_POLICY" -> Objects.equals(mapping.getTrustScorePolicyId(), draft.trustScorePolicyId());
            default -> false;
        };
        if (matches) {
            out.add(mapping);
        }
    }

    private boolean matchesRemediationTarget(RemediationRule rule, ParsedPosture posture, OffsetDateTime now) {
        if (rule == null || rule.isDeleted() || !"ACTIVE".equalsIgnoreCase(rule.getStatus())) {
            return false;
        }
        if (rule.getEffectiveFrom() != null && rule.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        if (rule.getEffectiveTo() != null && !rule.getEffectiveTo().isAfter(now)) {
            return false;
        }
        if (rule.getOsType() != null && !rule.getOsType().isBlank() && !equalsIgnoreCase(rule.getOsType(), posture.osType())) {
            return false;
        }
        return rule.getDeviceType() == null || rule.getDeviceType().isBlank() || equalsIgnoreCase(rule.getDeviceType(), posture.deviceType());
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
        map.put("status", canonicalRemediationStatus(r.getRemediationStatus()));

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
                canonicalRemediationStatus(row.getRemediationStatus())
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception _) {
            throw new RuntimeException("JSON serialization failed");
        }
    }

    private List<PriorOpenRemediation> loadPriorOpenRemediation(Long profileId, Long currentRunId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id,
                       r.posture_evaluation_run_id,
                       r.remediation_rule_id,
                       r.source_type,
                       r.remediation_status,
                       r.completed_at,
                       m.match_source,
                       m.system_information_rule_id,
                       m.reject_application_list_id,
                       m.trust_score_policy_id,
                       m.os_release_lifecycle_master_id
                  FROM posture_evaluation_remediation r
                  JOIN posture_evaluation_run run
                    ON run.id = r.posture_evaluation_run_id
             LEFT JOIN posture_evaluation_match m
                    ON m.id = r.posture_evaluation_match_id
                 WHERE run.device_trust_profile_id = :profileId
                   AND r.posture_evaluation_run_id <> :currentRunId
                   AND r.remediation_status IN (:openStatuses)
                 ORDER BY r.created_at ASC, r.id ASC
                """, new MapSqlParameterSource()
                .addValue("profileId", profileId)
                .addValue("currentRunId", currentRunId)
                .addValue("openStatuses", List.copyOf(com.e24online.mdm.utils.WorkflowStatusModel.OPEN_REMEDIATION_STATUSES)));

        List<PriorOpenRemediation> remediation = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            remediation.add(new PriorOpenRemediation(
                    longValue(row.get("id")),
                    longValue(row.get("posture_evaluation_run_id")),
                    longValue(row.get("remediation_rule_id")),
                    stringValue(row.get("source_type")),
                    stringValue(row.get("remediation_status")),
                    offsetDateTimeValue(row.get("completed_at")),
                    stringValue(row.get("match_source")),
                    longValue(row.get("system_information_rule_id")),
                    longValue(row.get("reject_application_list_id")),
                    longValue(row.get("trust_score_policy_id")),
                    longValue(row.get("os_release_lifecycle_master_id"))
            ));
        }
        return remediation;
    }

    private RemediationRescanKey currentKey(SavedRemediation savedRemediation, Map<Long, MatchDraft> currentMatchDraftsById) {
        if (savedRemediation == null || savedRemediation.remediation() == null) {
            return null;
        }
        PostureEvaluationRemediation remediation = savedRemediation.remediation();
        MatchDraft draft = remediation.getPostureEvaluationMatchId() == null
                ? null
                : currentMatchDraftsById.get(remediation.getPostureEvaluationMatchId());

        Long remediationRuleId = remediation.getRemediationRuleId();
        if (remediationRuleId == null) {
            var rule = savedRemediation.rule();
            remediationRuleId = rule != null ? rule.getId() : null;
        }

        String sourceType = normalizeUpper(remediation.getSourceType());
        String matchSource = draft != null ? normalizeUpper(draft.matchSource()) : null;
        Long systemRuleId = draft != null ? draft.systemRuleId() : null;
        Long rejectApplicationId = draft != null ? draft.rejectApplicationId() : null;
        Long trustScorePolicyId = draft != null ? draft.trustScorePolicyId() : null;
        Long osReleaseLifecycleMasterId = draft != null ? draft.osReleaseLifecycleMasterId() : null;

        return new RemediationRescanKey(
                remediationRuleId,
                sourceType,
                matchSource,
                systemRuleId,
                rejectApplicationId,
                trustScorePolicyId,
                osReleaseLifecycleMasterId
        );
    }

    private RemediationRescanKey priorKey(PriorOpenRemediation prior) {
        return new RemediationRescanKey(
                prior.remediationRuleId(),
                normalizeUpper(prior.sourceType()),
                normalizeUpper(prior.matchSource()),
                prior.systemInformationRuleId(),
                prior.rejectApplicationListId(),
                prior.trustScorePolicyId(),
                prior.osReleaseLifecycleMasterId()
        );
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case Number number -> number.longValue();

            case String text when !text.isBlank() -> {
                try {
                    yield Long.parseLong(text);
                } catch (NumberFormatException _) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private OffsetDateTime offsetDateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case OffsetDateTime offsetDateTime -> offsetDateTime;
            case Timestamp timestamp -> timestamp.toInstant().atOffset(ZoneOffset.UTC);
            case LocalDateTime localDateTime -> localDateTime.atOffset(ZoneOffset.UTC);
            case Instant instant -> instant.atOffset(ZoneOffset.UTC);
            case String text when !text.isBlank() -> {
                try {
                    yield OffsetDateTime.parse(text.trim());
                } catch (DateTimeParseException _) {
                    yield null;
                }
            }
            default -> null;
        };
    }

}
