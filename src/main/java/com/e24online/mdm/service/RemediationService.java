package com.e24online.mdm.service;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.records.posture.evaluation.*;
import com.e24online.mdm.repository.PostureEvaluationMatchRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import com.e24online.mdm.web.dto.RemediationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.e24online.mdm.utils.AgentWorkflowValueUtils.*;

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
    private final ObjectMapper objectMapper;
    private final Scheduler jdbcScheduler;

    public RemediationService(RuleRemediationMappingRepository ruleRemediationMappingRepository,
                              RemediationRuleRepository remediationRuleRepository,
                              PostureEvaluationRemediationRepository remediationRepository,
                              PostureEvaluationMatchRepository matchRepository,
                              ObjectMapper objectMapper,
                              Scheduler jdbcScheduler) {
        this.ruleRemediationMappingRepository = ruleRemediationMappingRepository;
        this.remediationRuleRepository = remediationRuleRepository;
        this.remediationRepository = remediationRepository;
        this.matchRepository = matchRepository;
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
                remediation.setRemediationStatus("PENDING");
                remediation.setInstructionOverride(rule.getInstructionJson());
                remediation.setCreatedAt(now);
                remediation.setCreatedBy("rule-engine");

                PostureEvaluationRemediation persistedRemediation = remediationRepository.save(remediation);
                saved.add(new SavedRemediation(persistedRemediation, rule, mapping.getEnforceMode()));
            }
        }

        return saved;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception _) {
            throw new RuntimeException("JSON serialization failed");
        }
    }
}
