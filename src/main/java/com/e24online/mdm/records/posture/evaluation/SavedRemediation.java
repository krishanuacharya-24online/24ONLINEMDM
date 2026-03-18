package com.e24online.mdm.records.posture.evaluation;

import com.e24online.mdm.domain.PostureEvaluationRemediation;
import com.e24online.mdm.domain.RemediationRule;

public record SavedRemediation(
        PostureEvaluationRemediation remediation,
        RemediationRule rule,
        String enforceMode
) {
}
