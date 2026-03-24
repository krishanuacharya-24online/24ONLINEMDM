package com.e24online.mdm.records.policy;

public record StarterTrustLevelSpec(String label,
                                    short scoreMin,
                                    short scoreMax,
                                    String decisionAction,
                                    boolean remediationRequired,
                                    String responseMessage) {
}
