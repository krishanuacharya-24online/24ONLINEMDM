package com.e24online.mdm.records.tenant;

import java.time.OffsetDateTime;
import java.util.List;

public record TenantSubscriptionResponse(
        Long tenantMasterId,
        String tenantId,
        String planCode,
        String planName,
        String subscriptionState,
        Integer maxActiveDevices,
        Integer maxTenantUsers,
        Long maxMonthlyPayloads,
        Integer dataRetentionDays,
        boolean premiumReportingEnabled,
        boolean advancedControlsEnabled,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        OffsetDateTime graceEndsAt,
        String notes,
        List<TenantFeatureOverrideResponse> featureOverrides
) {
}
