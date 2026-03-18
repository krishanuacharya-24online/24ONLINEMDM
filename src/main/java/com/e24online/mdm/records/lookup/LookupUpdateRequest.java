package com.e24online.mdm.records.lookup;

import jakarta.validation.constraints.NotBlank;

public record LookupUpdateRequest(@NotBlank String description) {
}