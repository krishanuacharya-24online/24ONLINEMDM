package com.e24online.mdm.records.posture.evaluation;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * Common records used across posture evaluation services.
 */
public record ParsedPosture(
        String tenantId,
        String deviceExternalId,
        String agentId,
        String osType,
        String osName,
        String osVersion,
        String osCycle,
        String deviceType,
        String timeZone,
        String kernelVersion,
        Integer apiLevel,
        String osBuildNumber,
        String manufacturer,
        Boolean rootDetected,
        Boolean runningOnEmulator,
        Boolean usbDebuggingStatus,
        OffsetDateTime captureTime,
        JsonNode root,
        JsonNode installedApps
) {
}
