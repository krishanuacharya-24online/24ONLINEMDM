package com.e24online.mdm.records.posture.evaluation;

import com.e24online.mdm.domain.PostureEvaluationMatch;

public record SavedMatch(PostureEvaluationMatch match, MatchDraft draft) {
}
