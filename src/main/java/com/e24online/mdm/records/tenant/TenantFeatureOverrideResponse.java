package com.e24online.mdm.records.tenant;

import java.time.OffsetDateTime;

public record TenantFeatureOverrideResponse(
        String featureKey,
        boolean enabled,
        OffsetDateTime expiresAt,
        String reason
) {
}

