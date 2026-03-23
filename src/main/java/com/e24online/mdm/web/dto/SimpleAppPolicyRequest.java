package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleAppPolicyRequest {

    private String policyTag;
    private Short severity;
    private String appName;
    private String publisher;
    private String packageId;
    private String appOsType;
    private String minAllowedVersion;
    private Long remediationRuleId;
    private String status;
}
