package com.e24online.mdm.records.devices;

import java.time.OffsetDateTime;

public record DeviceTokenRotation(
        Long enrollmentId,
        String enrollmentNo,
        String deviceToken,
        String tokenHint,
        OffsetDateTime deviceTokenExpiresAt
) {
}
