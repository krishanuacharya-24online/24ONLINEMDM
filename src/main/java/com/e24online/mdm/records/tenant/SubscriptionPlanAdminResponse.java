package com.e24online.mdm.records.tenant;

import java.time.OffsetDateTime;

public record SubscriptionPlanAdminResponse(
        Long id,
        String planCode,
        String planName,
        String description,
        Integer maxActiveDevices,
        Integer maxTenantUsers,
        Long maxMonthlyPayloads,
        Integer dataRetentionDays,
        boolean premiumReportingEnabled,
        boolean advancedControlsEnabled,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime modifiedAt
) {
}
