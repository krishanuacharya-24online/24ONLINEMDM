package com.e24online.mdm.records.devices;

public record DeviceTokenPrincipal(
        String tenantId,
        String enrollmentNo,
        Long enrollmentId
) {
}
