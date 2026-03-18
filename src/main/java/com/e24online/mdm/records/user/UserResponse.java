package com.e24online.mdm.records.user;

public record UserResponse(
        Long id,
        String username,
        String role,
        String status,
        String tenantId
) {
}