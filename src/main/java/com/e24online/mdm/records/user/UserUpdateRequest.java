package com.e24online.mdm.records;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record UserUpdateRequest(
        @NotBlank String role,
        String status,
        @JsonAlias({"tenant_id", "tenantId"})
        String tenantId,
        String password
) {
}
