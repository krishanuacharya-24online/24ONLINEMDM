package com.e24online.mdm.records;

public record LoginResponse(String username, String role, Long tenantId, boolean passwordBreached) {
}
