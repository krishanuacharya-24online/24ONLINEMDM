package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimplePolicySimulationAppInput {

    private String appName;
    private String packageId;
    private String appVersion;
    private String publisher;
    private String appOsType;
}
