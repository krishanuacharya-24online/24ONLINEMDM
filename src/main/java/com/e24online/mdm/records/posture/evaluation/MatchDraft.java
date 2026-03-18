package com.e24online.mdm.records.posture.evaluation;

public record MatchDraft(
        String matchSource,
        Long systemRuleId,
        Long rejectApplicationId,
        Long trustScorePolicyId,
        Long osReleaseLifecycleMasterId,
        String osLifecycleState,
        Long deviceInstalledApplicationId,
        Short severity,
        String complianceAction,
        short scoreDelta,
        String matchDetail
) {
}
