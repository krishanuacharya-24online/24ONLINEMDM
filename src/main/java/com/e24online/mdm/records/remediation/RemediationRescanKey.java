package com.e24online.mdm.records.remediation;

public record RemediationRescanKey(
        Long remediationRuleId,
        String sourceType,
        String matchSource,
        Long systemInformationRuleId,
        Long rejectApplicationListId,
        Long trustScorePolicyId,
        Long osReleaseLifecycleMasterId
) {
}
