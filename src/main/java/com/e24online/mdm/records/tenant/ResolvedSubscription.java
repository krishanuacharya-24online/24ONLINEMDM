package com.e24online.mdm.records.tenant;

import com.e24online.mdm.domain.SubscriptionPlan;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantSubscription;

import java.util.Map;

public record ResolvedSubscription(
        Tenant tenant,
        TenantSubscription subscription,
        SubscriptionPlan plan,
        Map<String, Boolean> featureOverrides
) {
}
