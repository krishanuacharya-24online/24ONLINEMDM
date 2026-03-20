package com.e24online.mdm.records.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.OffsetDateTime;

public record TenantFeatureOverrideRequest(
        Boolean enabled,
        @JsonAlias("expires_at") OffsetDateTime expiresAt,
        String reason
) {
}

