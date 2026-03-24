package com.e24online.mdm.records.policy;

public record TemplateRejectApp(String key,
                                String policyTag,
                                short severity,
                                String appName,
                                String publisher,
                                String packageId,
                                String appOsType,
                                String minAllowedVersion) {
}
