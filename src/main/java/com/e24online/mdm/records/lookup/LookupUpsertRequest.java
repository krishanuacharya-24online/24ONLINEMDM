package com.e24online.mdm.records.lookup;

import jakarta.validation.constraints.NotBlank;

public record LookupUpsertRequest(
        @NotBlank String code,
        @NotBlank String description
) {
}