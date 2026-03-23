package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SimplePolicySimulationRequest {

    private Integer currentScore;
    private String deviceExternalId;
    private String osType;
    private String osName;
    private String osVersion;
    private String osCycle;
    private String deviceType;
    private String timeZone;
    private String kernelVersion;
    private Integer apiLevel;
    private String osBuildNumber;
    private String manufacturer;
    private Boolean rootDetected;
    private Boolean runningOnEmulator;
    private Boolean usbDebuggingStatus;
    private List<SimplePolicySimulationAppInput> installedApps = new ArrayList<>();

    public void setInstalledApps(List<SimplePolicySimulationAppInput> installedApps) {
        this.installedApps = installedApps == null ? new ArrayList<>() : installedApps;
    }
}
