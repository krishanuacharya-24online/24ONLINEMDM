package com.e24online.mdm.records;

import java.time.OffsetDateTime;

public record PolicyTemplateApplyReport(
        String packName,
        String actor,
        boolean includeTenantScopes,
        boolean clearPolicyAudit,
        int clearedPolicyAuditRows,
        int retiredSystemRules,
        int retiredSystemRuleConditions,
        int retiredRejectApps,
        int retiredTrustScorePolicies,
        int retiredTrustDecisionPolicies,
        int retiredRemediationRules,
        int retiredRuleRemediationMappings,
        int appliedSystemRules,
        int appliedSystemRuleConditions,
        int appliedRejectApps,
        int appliedTrustScorePolicies,
        int appliedTrustDecisionPolicies,
        int appliedRemediationRules,
        int appliedRuleRemediationMappings,
        OffsetDateTime appliedAt
) {
}
