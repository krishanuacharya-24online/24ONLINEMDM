package com.e24online.mdm.records.reports;

public record FleetOperationalSummaryResponse(
        String scopeTenantId,
        int staleAfterHours,
        long totalDevices,
        long staleDevices,
        long highRiskDevices,
        long criticalDevices,
        long lifecycleRiskDevices,
        long supportedDevices,
        long eolDevices,
        long eeolDevices,
        long notTrackedDevices
) {
}
