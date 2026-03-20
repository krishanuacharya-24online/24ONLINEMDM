package com.e24online.mdm.records.posture.evaluation;

import java.time.OffsetDateTime;

public record RemediationStatusTransition(
        Long remediationId,
        Long postureEvaluationRunId,
        Long remediationRuleId,
        String sourceType,
        String matchSource,
        String fromStatus,
        String toStatus,
        OffsetDateTime completedAt
) {
}
