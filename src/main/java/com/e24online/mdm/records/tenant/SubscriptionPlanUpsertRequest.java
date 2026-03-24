package com.e24online.mdm.records.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SubscriptionPlanUpsertRequest(
        @JsonAlias("plan_code") String planCode,
        @JsonAlias("plan_name") String planName,
        String description,
        @JsonAlias("max_active_devices") Integer maxActiveDevices,
        @JsonAlias("max_tenant_users") Integer maxTenantUsers,
        @JsonAlias("max_monthly_payloads") Long maxMonthlyPayloads,
        @JsonAlias("data_retention_days") Integer dataRetentionDays,
        @JsonAlias("premium_reporting_enabled") Boolean premiumReportingEnabled,
        @JsonAlias("advanced_controls_enabled") Boolean advancedControlsEnabled,
        String status
) {
}
