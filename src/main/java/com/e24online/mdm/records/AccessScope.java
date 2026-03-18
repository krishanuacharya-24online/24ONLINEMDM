package com.e24online.mdm.records;

public record AccessScope(
        String actor,
        String role,
        Long tenantId,
        boolean productAdmin,
        boolean tenantAdmin
) {
}