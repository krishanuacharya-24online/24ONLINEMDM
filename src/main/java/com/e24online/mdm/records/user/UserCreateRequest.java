package com.e24online.mdm.records.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String role,
        String status,
        @JsonAlias({"tenant_id", "tenantId"})
        String tenantId
) {
}
