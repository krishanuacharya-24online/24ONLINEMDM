package com.e24online.mdm.records.policy;

public record StarterAppRuleSpec(String policyTag,
                                 String appOsType,
                                 String appName,
                                 String packageId,
                                 String publisher,
                                 String minAllowedVersion,
                                 short severity,
                                 String remediationTitle) {
}
