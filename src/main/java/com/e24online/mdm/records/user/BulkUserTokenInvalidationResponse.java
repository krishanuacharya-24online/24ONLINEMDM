package com.e24online.mdm.records.user;

public record BulkUserTokenInvalidationResponse(
        long invalidatedUserCount,
        long skippedProtectedUserCount,
        long revokedRefreshTokenCount
) {
}
