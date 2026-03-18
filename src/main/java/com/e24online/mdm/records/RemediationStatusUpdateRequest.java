package com.e24online.mdm.records;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public record RemediationStatusUpdateRequest(
        @NotBlank
        @JsonAlias({"remediation_status", "remediationStatus"})
        String remediationStatus,
        @JsonAlias({"completed_at", "completedAt"})
        OffsetDateTime completedAt
) {
}
