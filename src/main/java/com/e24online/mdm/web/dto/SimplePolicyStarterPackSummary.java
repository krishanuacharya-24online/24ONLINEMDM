package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimplePolicyStarterPackSummary {

    private String scope;
    private int createdDeviceChecks;
    private int skippedDeviceChecks;
    private int createdAppRules;
    private int skippedAppRules;
    private int createdTrustLevels;
    private int skippedTrustLevels;
    private int createdFixes;
    private int skippedFixes;
}
