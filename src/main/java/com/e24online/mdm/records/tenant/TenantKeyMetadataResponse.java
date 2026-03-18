package com.e24online.mdm.records.tenant;

import java.time.OffsetDateTime;

public record TenantKeyMetadataResponse(
        Long tenantMasterId,
        String tenantId,
        boolean active,
        String keyHint,
        OffsetDateTime createdAt
) {
}
