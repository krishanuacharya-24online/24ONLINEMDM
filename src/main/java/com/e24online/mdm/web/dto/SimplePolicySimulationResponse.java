package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SimplePolicySimulationResponse {

    private Short scoreBefore;
    private Short scoreAfter;
    private Short scoreDeltaTotal;
    private Integer matchedRuleCount;
    private Integer matchedAppCount;
    private String decisionAction;
    private String decisionReason;
    private boolean remediationRequired;
    private String lifecycleState;
    private String lifecycleSignalKey;
    private List<SimplePolicySimulationFinding> findings = new ArrayList<>();

    public void setFindings(List<SimplePolicySimulationFinding> findings) {
        this.findings = findings == null ? new ArrayList<>() : findings;
    }
}
