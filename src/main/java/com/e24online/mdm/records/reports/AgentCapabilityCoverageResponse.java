package com.e24online.mdm.records.reports;

import java.time.OffsetDateTime;

public record AgentCapabilityCoverageResponse(
        String capabilityKey,
        long deviceCount,
        OffsetDateTime latestCaptureTime
) {
}
