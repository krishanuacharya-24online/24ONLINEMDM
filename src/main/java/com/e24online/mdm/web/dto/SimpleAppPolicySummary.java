package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleAppPolicySummary {

    private Long id;
    private String policyTag;
    private Short severity;
    private Short scoreDelta;
    private String appName;
    private String publisher;
    private String packageId;
    private String appOsType;
    private String minAllowedVersion;
    private Long remediationRuleId;
    private String remediationTitle;
    private String status;
    private boolean complex;
    private String complexityReason;
}
