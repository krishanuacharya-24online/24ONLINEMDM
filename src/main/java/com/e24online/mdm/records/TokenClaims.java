package com.e24online.mdm.records;

public record TokenClaims(String username, String role, Long userId, Long tenantId, Long tokenVersion) {
    public boolean isValid() {
        return username != null
                && !username.isBlank()
                && role != null
                && !role.isBlank()
                && userId != null
                && tokenVersion != null;
    }
}
