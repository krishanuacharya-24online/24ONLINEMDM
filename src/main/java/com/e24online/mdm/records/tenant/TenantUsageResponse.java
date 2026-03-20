package com.e24online.mdm.records.tenant;

import java.time.LocalDate;

public record TenantUsageResponse(
        Long tenantMasterId,
        String tenantId,
        LocalDate usageMonth,
        long activeDeviceCount,
        long activeUserCount,
        long posturePayloadCount,
        Integer maxActiveDevices,
        Integer maxTenantUsers,
        Long maxMonthlyPayloads,
        boolean devicesOverLimit,
        boolean usersOverLimit,
        boolean payloadsOverLimit
) {
}
