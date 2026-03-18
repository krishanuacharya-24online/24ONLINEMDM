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
        this(payloadId,
                status,
                null,
                evaluationRunId,
                decisionResponseId,
                decisionAction,
                trustScore,
                decisionReason,
                remediationRequired,
                remediation);
    }

    public void setRemediation(List<RemediationSummary> remediation) {
        this.remediation = remediation != null ? remediation : new ArrayList<>();
    }

    public Boolean isRemediationRequired() {
        return remediationRequired;
    }

    public void suppressQueuedRemediationFields() {
        this.remediationRequired = null;
        this.remediation = null;
    }

}
