package com.e24online.mdm.records.posture.evaluation;

import com.e24online.mdm.domain.RuleRemediationMapping;

public record RemediationCandidate(RuleRemediationMapping mapping, Long matchId, String sourceType) {
}
