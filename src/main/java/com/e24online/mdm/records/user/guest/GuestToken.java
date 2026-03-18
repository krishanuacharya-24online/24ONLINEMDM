package com.e24online.mdm.records.user.guest;

public record GuestToken(
        String token,
        String resourceId
) {
}
