package com.e24online.mdm.records.user;

public record LoginResponse(String username, String role, Long tenantId, boolean passwordBreached) {
}
