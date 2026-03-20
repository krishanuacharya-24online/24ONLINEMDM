package com.e24online.mdm.records.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.OffsetDateTime;

public record TenantSubscriptionUpsertRequest(
        @JsonAlias("plan_code") String planCode,
        @JsonAlias("subscription_state") String subscriptionState,
        @JsonAlias("current_period_start") OffsetDateTime currentPeriodStart,
        @JsonAlias("current_period_end") OffsetDateTime currentPeriodEnd,
        @JsonAlias("grace_ends_at") OffsetDateTime graceEndsAt,
        String notes
) {
}

