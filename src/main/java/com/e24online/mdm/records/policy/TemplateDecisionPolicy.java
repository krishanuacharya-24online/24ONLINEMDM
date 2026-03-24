package com.e24online.mdm.records.policy;

public record TemplateDecisionPolicy(String policyName,
                                     short scoreMin,
                                     short scoreMax,
                                     String decisionAction,
                                     boolean remediationRequired,
                                     String responseMessage) {
}
