package com.e24online.mdm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PosturePayloadIngestResponse {

    private Long payloadId;
    private String status;
    private String resultStatusUrl;
    private Long evaluationRunId;
    private Long decisionResponseId;
    private String decisionAction;
    private Short trustScore;
    private String decisionReason;
    private String schemaCompatibilityStatus;
    private List<String> validationWarnings = new ArrayList<>();
    private Boolean remediationRequired = Boolean.FALSE;
    private List<RemediationSummary> remediation = new ArrayList<>();

    public PosturePayloadIngestResponse(Long payloadId,
                                        String status,
                                        Long evaluationRunId,
                                        Long decisionResponseId,
                                        String decisionAction,
                                        Short trustScore,
                                        String decisionReason,
                                        boolean remediationRequired,
                                        List<RemediationSummary> remediation) {
        this.payloadId = payloadId;
        this.status = status;
        this.resultStatusUrl = null;
        this.evaluationRunId = evaluationRunId;
        this.decisionResponseId = decisionResponseId;
        this.decisionAction = decisionAction;
        this.trustScore = trustScore;
        this.decisionReason = decisionReason;
        this.schemaCompatibilityStatus = null;
        this.validationWarnings = new ArrayList<>();
        this.remediationRequired = remediationRequired;
        this.remediation = remediation != null ? remediation : new ArrayList<>();
    }

    public void setRemediation(List<RemediationSummary> remediation) {
        this.remediation = remediation != null ? remediation : new ArrayList<>();
    }

    public void setValidationWarnings(List<String> validationWarnings) {
        this.validationWarnings = validationWarnings != null ? validationWarnings : new ArrayList<>();
    }

    public Boolean isRemediationRequired() {
        return remediationRequired;
    }

    public void suppressQueuedRemediationFields() {
        this.remediationRequired = null;
        this.remediation = null;
    }

}
