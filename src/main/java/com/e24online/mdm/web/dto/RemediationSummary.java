package com.e24online.mdm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RemediationSummary {

    private Long evaluationRemediationId;
    private Long remediationRuleId;
    private String remediationCode;
    private String title;
    private String description;
    private String remediationType;
    private String enforceMode;
    private String instructionJson;
    private String remediationStatus;

}
