package com.e24online.mdm.records;

import java.time.OffsetDateTime;

public record AgentEnrollmentClaim(
        String enrollmentNo,
        String deviceToken,
        String tokenHint,
        OffsetDateTime deviceTokenExpiresAt
) {
}
