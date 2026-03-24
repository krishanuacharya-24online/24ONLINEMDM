package com.e24online.mdm.records.policy;

public record TemplateMapping(String sourceType,
                              String systemRuleCode,
                              String rejectAppKey,
                              String trustPolicyCode,
                              String decisionAction,
                              String remediationCode,
                              String enforceMode,
                              short rankOrder) {

    public static TemplateMapping forSystemRule(String systemRuleCode,
                                                String remediationCode,
                                                String enforceMode,
                                                short rankOrder) {
        return new TemplateMapping("SYSTEM_RULE", systemRuleCode, null, null, null, remediationCode, enforceMode, rankOrder);
    }

    public static TemplateMapping forRejectApp(String rejectAppKey,
                                               String remediationCode,
                                               String enforceMode,
                                               short rankOrder) {
        return new TemplateMapping("REJECT_APPLICATION", null, rejectAppKey, null, null, remediationCode, enforceMode, rankOrder);
    }

    public static TemplateMapping forTrustPolicy(String trustPolicyCode,
                                                 String remediationCode,
                                                 String enforceMode,
                                                 short rankOrder) {
        return new TemplateMapping("TRUST_POLICY", null, null, trustPolicyCode, null, remediationCode, enforceMode, rankOrder);
    }
}
