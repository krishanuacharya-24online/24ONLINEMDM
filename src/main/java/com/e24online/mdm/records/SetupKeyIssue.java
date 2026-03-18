package com.e24online.mdm.records;

import java.time.OffsetDateTime;

public record SetupKeyIssue(
        Long setupKeyId,
        String setupKey,
        String keyHint,
        OffsetDateTime expiresAt,
        Integer maxUses,
        Long targetUserId,
        Long issuedByUserId
) {
}
