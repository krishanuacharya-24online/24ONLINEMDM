package com.e24online.mdm.records.tenant;

public record SubscriptionPlanResponse(
        String planCode,
        String planName,
        String description,
        Integer maxActiveDevices,
        Integer maxTenantUsers,
        Long maxMonthlyPayloads,
        Integer dataRetentionDays,
        boolean premiumReportingEnabled,
        boolean advancedControlsEnabled
) {
}

