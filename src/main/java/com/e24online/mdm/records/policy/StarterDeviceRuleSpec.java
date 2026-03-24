package com.e24online.mdm.records.policy;

public record StarterDeviceRuleSpec(String name,
                                    String description,
                                    String osType,
                                    String deviceType,
                                    short severity,
                                    String fieldName,
                                    String operator,
                                    String valueType,
                                    String valueText,
                                    Double valueNumeric,
                                    Boolean valueBoolean,
                                    String remediationTitle) {
}
