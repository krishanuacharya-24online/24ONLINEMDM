package com.e24online.mdm.records.tenant;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record TenantUpsertRequest(
        @NotBlank @JsonAlias("tenant_id") String tenantId,
        @NotBlank String name,
        String status
) {
}