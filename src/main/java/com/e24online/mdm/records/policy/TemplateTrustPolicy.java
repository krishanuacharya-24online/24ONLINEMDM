package com.e24online.mdm.records.policy;

public record TemplateTrustPolicy(String policyCode,
                                  String sourceType,
                                  String signalKey,
                                  Short severity,
                                  String complianceAction,
                                  short scoreDelta,
                                  double weight) {
}
