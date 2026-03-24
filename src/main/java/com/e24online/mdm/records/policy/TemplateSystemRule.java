package com.e24online.mdm.records.policy;

public record TemplateSystemRule(String ruleCode,
                                 String ruleTag,
                                 String description,
                                 String osType,
                                 String deviceType,
                                 short severity,
                                 int priority,
                                 String complianceAction,
                                 short riskScoreDelta,
                                 String fieldName,
                                 String operator,
                                 String valueText,
                                 Double valueNumeric,
                                 Boolean valueBoolean) {
}
