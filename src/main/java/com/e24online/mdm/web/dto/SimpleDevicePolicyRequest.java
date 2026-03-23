package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleDevicePolicyRequest {

    private String name;
    private String description;
    private String osType;
    private String osName;
    private String deviceType;
    private Short severity;
    private String fieldName;
    private String operator;
    private String valueType;
    private String valueText;
    private Double valueNumeric;
    private Boolean valueBoolean;
    private Long remediationRuleId;
    private String status;
}
