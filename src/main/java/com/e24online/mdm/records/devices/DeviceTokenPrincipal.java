package com.e24online.mdm.records;

public record DeviceTokenPrincipal(
        String tenantId,
        String enrollmentNo,
        Long enrollmentId
) {
}
