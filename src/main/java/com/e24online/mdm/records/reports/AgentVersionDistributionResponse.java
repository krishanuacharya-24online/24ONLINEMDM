package com.e24online.mdm.records.reports;

import java.time.OffsetDateTime;

public record AgentVersionDistributionResponse(
        String agentVersion,
        String schemaCompatibilityStatus,
        long deviceCount,
        long devicesWithCapabilities,
        OffsetDateTime latestCaptureTime
) {
}
