package com.e24online.mdm.records.posture.evaluation;

public record ScoreSignal(
        String eventSource,
        Long sourceRecordId,
        Long trustScorePolicyId,
        Long systemRuleId,
        Long rejectApplicationId,
        Long osReleaseLifecycleMasterId,
        String osLifecycleState,
        short scoreDelta,
        String notes
) {
}

