package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.domain.PostureEvaluationMatch;
import com.e24online.mdm.domain.PostureEvaluationRemediation;
import com.e24online.mdm.domain.PostureEvaluationRun;
import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.RuleRemediationMapping;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.RemediationStatusTransition;
import com.e24online.mdm.records.posture.evaluation.SavedMatch;
import com.e24online.mdm.records.posture.evaluation.SavedRemediation;
import com.e24online.mdm.repository.PostureEvaluationMatchRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemediationServiceTest {

    @Mock
    private RuleRemediationMappingRepository mappingRepository;

    @Mock
    private RemediationRuleRepository remediationRuleRepository;

    @Mock
    private PostureEvaluationRemediationRepository remediationRepository;

    @Mock
    private PostureEvaluationMatchRepository matchRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private ObjectMapper objectMapper;
    private RemediationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RemediationService(
                mappingRepository,
                remediationRuleRepository,
                remediationRepository,
                matchRepository,
                jdbc,
                objectMapper,
                reactor.core.scheduler.Schedulers.immediate()
        );
    }

    @Test
    void saveRemediation_createsFromMatchAndDecisionAndDeduplicates() {
        OffsetDateTime now = OffsetDateTime.now();
        PostureEvaluationRun run = new PostureEvaluationRun();
        run.setId(10L);
        run.setDecisionAction("BLOCK");

        PostureEvaluationMatch match = new PostureEvaluationMatch();
        match.setId(100L);
        SavedMatch savedMatch = new SavedMatch(
                match,
                new MatchDraft("SYSTEM_RULE", 1L, null, null, null, "SUPPORTED", null, (short) 3, "ALLOW", (short) -5, "{}")
        );

        RuleRemediationMapping fromRule = mapping(11L, "SYSTEM_RULE", 1L, null, 200L, "AUTO", (short) 1, null);
        RuleRemediationMapping duplicateFromRule = mapping(12L, "SYSTEM_RULE", 1L, null, 200L, "AUTO", (short) 2, null);
        RuleRemediationMapping fromDecision = mapping(13L, "DECISION", null, "BLOCK", 201L, "MANUAL", (short) 3, null);

        RemediationRule r200 = remediationRule(200L, "WINDOWS", "LAPTOP", true);
        RemediationRule r201 = remediationRule(201L, null, null, true);

        when(mappingRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(fromRule, duplicateFromRule, fromDecision));
        when(remediationRuleRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(r200, r201));
        AtomicLong id = new AtomicLong(500L);
        when(remediationRepository.save(any(PostureEvaluationRemediation.class))).thenAnswer(invocation -> {
            PostureEvaluationRemediation row = invocation.getArgument(0);
            row.setId(id.incrementAndGet());
            return row;
        });

        List<SavedRemediation> remediations = service.saveRemediation(
                run,
                List.of(savedMatch),
                posture("WINDOWS", "LAPTOP"),
                now
        );

        assertNotNull(remediations);
        assertEquals(2, remediations.size()); // duplicate match mapping is deduped
        assertEquals(200L, remediations.get(0).rule().getId());
        assertEquals(201L, remediations.get(1).rule().getId());
        assertEquals("PROPOSED", remediations.get(0).remediation().getRemediationStatus());
    }

    @Test
    void saveRemediation_filtersOutMismatchedTargets() {
        OffsetDateTime now = OffsetDateTime.now();
        PostureEvaluationRun run = new PostureEvaluationRun();
        run.setId(20L);
        run.setDecisionAction("ALLOW");

        PostureEvaluationMatch match = new PostureEvaluationMatch();
        match.setId(200L);
        SavedMatch savedMatch = new SavedMatch(
                match,
                new MatchDraft("SYSTEM_RULE", 2L, null, null, null, "SUPPORTED", null, (short) 2, "ALLOW", (short) -2, "{}")
        );

        RuleRemediationMapping mapping = this.mapping(21L, "SYSTEM_RULE", 2L, null, 300L, "AUTO", (short) 1, null);
        RemediationRule mismatchRule = remediationRule(300L, "IOS", "PHONE", true);

        when(mappingRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(mapping));
        when(remediationRuleRepository.findActiveForEvaluation(anyString(), any(OffsetDateTime.class))).thenReturn(List.of(mismatchRule));

        List<SavedRemediation> remediations = service.saveRemediation(
                run,
                List.of(savedMatch),
                posture("WINDOWS", "LAPTOP"),
                now
        );

        assertNotNull(remediations);
        assertFalse(remediations.iterator().hasNext());
    }

    @Test
    void buildDecisionPayload_andBuildResponse_includeExpectedFields() {
        PostureEvaluationRun run = new PostureEvaluationRun();
        run.setId(30L);
        run.setDecisionAction("NOTIFY");
        run.setTrustScoreAfter((short) 72);
        run.setDecisionReason("risk");
        run.setRemediationRequired(true);

        PostureEvaluationRemediation remediation = new PostureEvaluationRemediation();
        remediation.setId(401L);
        remediation.setRemediationStatus("PROPOSED");
        remediation.setInstructionOverride("{\"steps\":[\"do-a\"]}");
        RemediationRule rule = remediationRule(402L, null, null, true);
        SavedRemediation savedRemediation = new SavedRemediation(remediation, rule, "AUTO");

        String payload = service.buildDecisionPayload(run, List.of(savedRemediation));
        assertNotNull(payload);
        assertFalse(payload.isBlank());
        assertTrueContains(payload, "\"decision_action\":\"NOTIFY\"");
        assertTrueContains(payload, "\"remediation_required\":true");

        DevicePosturePayload sourcePayload = new DevicePosturePayload();
        sourcePayload.setId(900L);
        sourcePayload.setProcessStatus("EVALUATED");

        DeviceDecisionResponse decision = new DeviceDecisionResponse();
        decision.setId(901L);
        decision.setDecisionAction("NOTIFY");
        decision.setTrustScore((short) 72);

        PosturePayloadIngestResponse response = service.buildResponse(
                sourcePayload,
                run,
                decision,
                List.of(savedRemediation)
        );

        assertEquals(900L, response.getPayloadId());
        assertEquals(30L, response.getEvaluationRunId());
        assertEquals(901L, response.getDecisionResponseId());
        assertEquals(1, response.getRemediation().size());
    }

    @Test
    void reconcilePriorOpenRemediations_marksMatchingPriorRowsStillOpen() {
        OffsetDateTime now = OffsetDateTime.now();

        PostureEvaluationRun currentRun = new PostureEvaluationRun();
        currentRun.setId(10L);
        currentRun.setDeviceTrustProfileId(20L);

        PostureEvaluationMatch match = new PostureEvaluationMatch();
        match.setId(100L);
        SavedMatch currentMatch = new SavedMatch(
                match,
                new MatchDraft("SYSTEM_RULE", 1L, null, null, null, "SUPPORTED", 99L, (short) 3, "BLOCK", (short) -10, "{}")
        );

        PostureEvaluationRemediation currentRemediation = new PostureEvaluationRemediation();
        currentRemediation.setId(501L);
        currentRemediation.setPostureEvaluationRunId(10L);
        currentRemediation.setRemediationRuleId(200L);
        currentRemediation.setPostureEvaluationMatchId(100L);
        currentRemediation.setSourceType("MATCH");
        currentRemediation.setRemediationStatus("PROPOSED");

        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(priorRow(
                900L,
                8L,
                200L,
                "MATCH",
                "USER_ACKNOWLEDGED",
                now.minusDays(1),
                "SYSTEM_RULE",
                1L,
                null,
                null,
                null
        )));
        when(jdbc.batchUpdate(anyString(), any(MapSqlParameterSource[].class))).thenReturn(new int[]{1});

        List<RemediationStatusTransition> transitions = service.reconcilePriorOpenRemediations(
                currentRun,
                List.of(currentMatch),
                List.of(new SavedRemediation(currentRemediation, remediationRule(200L, null, null, true), "AUTO")),
                now
        );

        assertEquals(1, transitions.size());
        assertEquals("USER_ACKNOWLEDGED", transitions.getFirst().fromStatus());
        assertEquals("STILL_OPEN", transitions.getFirst().toStatus());
        assertNull(transitions.getFirst().completedAt());

        ArgumentCaptor<MapSqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(jdbc).batchUpdate(anyString(), batchCaptor.capture());
        assertEquals("STILL_OPEN", batchCaptor.getValue()[0].getValue("status"));
        assertNull(batchCaptor.getValue()[0].getValue("completedAt"));
    }

    @Test
    void reconcilePriorOpenRemediations_marksMissingPriorRowsResolvedOnRescan() {
        OffsetDateTime now = OffsetDateTime.now();

        PostureEvaluationRun currentRun = new PostureEvaluationRun();
        currentRun.setId(10L);
        currentRun.setDeviceTrustProfileId(20L);

        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(priorRow(
                901L,
                7L,
                201L,
                "DECISION",
                "DELIVERED",
                null,
                null,
                null,
                null,
                null,
                null
        )));
        when(jdbc.batchUpdate(anyString(), any(MapSqlParameterSource[].class))).thenReturn(new int[]{1});

        List<RemediationStatusTransition> transitions = service.reconcilePriorOpenRemediations(
                currentRun,
                List.of(),
                List.of(),
                now
        );

        assertEquals(1, transitions.size());
        assertEquals("DELIVERED", transitions.getFirst().fromStatus());
        assertEquals("RESOLVED_ON_RESCAN", transitions.getFirst().toStatus());
        assertEquals(now, transitions.getFirst().completedAt());

        ArgumentCaptor<MapSqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(jdbc).batchUpdate(anyString(), batchCaptor.capture());
        assertEquals("RESOLVED_ON_RESCAN", batchCaptor.getValue()[0].getValue("status"));
        assertEquals(now, batchCaptor.getValue()[0].getValue("completedAt"));
    }

    private RuleRemediationMapping mapping(Long id,
                                           String sourceType,
                                           Long systemRuleId,
                                           String decisionAction,
                                           Long remediationRuleId,
                                           String enforceMode,
                                           Short rank,
                                           OffsetDateTime effectiveFrom) {
        RuleRemediationMapping mapping = new RuleRemediationMapping();
        mapping.setId(id);
        mapping.setSourceType(sourceType);
        mapping.setSystemInformationRuleId(systemRuleId);
        mapping.setDecisionAction(decisionAction);
        mapping.setRemediationRuleId(remediationRuleId);
        mapping.setEnforceMode(enforceMode);
        mapping.setRankOrder(rank);
        mapping.setStatus("ACTIVE");
        mapping.setDeleted(false);
        mapping.setEffectiveFrom(effectiveFrom);
        mapping.setEffectiveTo(null);
        return mapping;
    }

    private RemediationRule remediationRule(Long id, String osType, String deviceType, boolean active) {
        RemediationRule rule = new RemediationRule();
        rule.setId(id);
        rule.setRemediationCode("R-" + id);
        rule.setTitle("Title " + id);
        rule.setDescription("Desc " + id);
        rule.setRemediationType("SCRIPT");
        rule.setInstructionJson("{\"cmd\":\"echo ok\"}");
        rule.setOsType(osType);
        rule.setDeviceType(deviceType);
        rule.setStatus(active ? "ACTIVE" : "INACTIVE");
        rule.setDeleted(false);
        rule.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        rule.setEffectiveTo(OffsetDateTime.now().plusDays(1));
        return rule;
    }

    private ParsedPosture posture(String osType, String deviceType) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode apps = objectMapper.createArrayNode();
        return new ParsedPosture(
                "tenant-a",
                "dev-1",
                "agent-1",
                osType,
                "WINDOWS 11",
                "11.0",
                "11",
                deviceType,
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
                apps
        );
    }

    private void assertTrueContains(String value, String expectedPart) {
        if (!value.contains(expectedPart)) {
            throw new AssertionError("Expected payload to contain: " + expectedPart + " but was: " + value);
        }
    }

    private Map<String, Object> priorRow(Long remediationId,
                                         Long runId,
                                         Long remediationRuleId,
                                         String sourceType,
                                         String remediationStatus,
                                         OffsetDateTime completedAt,
                                         String matchSource,
                                         Long systemRuleId,
                                         Long rejectApplicationId,
                                         Long trustScorePolicyId,
                                         Long osReleaseLifecycleMasterId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", remediationId);
        row.put("posture_evaluation_run_id", runId);
        row.put("remediation_rule_id", remediationRuleId);
        row.put("source_type", sourceType);
        row.put("remediation_status", remediationStatus);
        row.put("completed_at", completedAt);
        row.put("match_source", matchSource);
        row.put("system_information_rule_id", systemRuleId);
        row.put("reject_application_list_id", rejectApplicationId);
        row.put("trust_score_policy_id", trustScorePolicyId);
        row.put("os_release_lifecycle_master_id", osReleaseLifecycleMasterId);
        return row;
    }
}
