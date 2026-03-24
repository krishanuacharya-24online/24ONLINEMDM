package com.e24online.mdm.records.remediation;

import java.time.OffsetDateTime;

public record PriorOpenRemediation(
        Long id,
        Long postureEvaluationRunId,
        Long remediationRuleId,
        String sourceType,
        String remediationStatus,
        OffsetDateTime completedAt,
        String matchSource,
        Long systemInformationRuleId,
        Long rejectApplicationListId,
        Long trustScorePolicyId,
        Long osReleaseLifecycleMasterId
) {
}
