package com.e24online.mdm.records.policy;

public record TemplateRemediation(String code,
                                  String title,
                                  String description,
                                  String remediationType,
                                  String osType,
                                  String deviceType,
                                  short priority,
                                  String instructionJson) {
}
