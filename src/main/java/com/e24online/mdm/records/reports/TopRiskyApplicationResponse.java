package com.e24online.mdm.records.reports;

import java.time.OffsetDateTime;

public record TopRiskyApplicationResponse(
        String appName,
        String packageId,
        String publisher,
        String appOsType,
        String policyTag,
        long impactedDevices,
        long blockedDevices,
        long currentMatchCount,
        OffsetDateTime latestEvaluatedAt
) {
}
