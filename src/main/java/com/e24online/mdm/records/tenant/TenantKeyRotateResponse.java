package com.e24online.mdm.records.tenant;

import java.time.OffsetDateTime;

public record TenantKeyRotateResponse(
        Long tenantMasterId,
        String tenantId,
        String key,
        String keyHint,
        OffsetDateTime createdAt
) {
}
