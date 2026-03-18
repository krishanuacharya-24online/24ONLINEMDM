package com.e24online.mdm.records.posture.evaluation;

import java.util.List;

public record EvaluationComputation(
        short scoreBefore,
        short scoreAfter,
        short scoreDeltaTotal,
        int matchedRuleCount,
        int matchedAppCount,
        String decisionAction,
        String decisionReason,
        boolean remediationRequired,
        Long decisionPolicyId,
        List<MatchDraft> matches,
        List<ScoreSignal> scoreSignals
) {
}
