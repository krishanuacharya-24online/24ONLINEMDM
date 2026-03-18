package com.e24online.mdm.records;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QrClaimRequest(
        @NotBlank
        @Size(max = 512)
        @JsonAlias({"qr_token", "qrToken"})
        String qrToken,
        @NotBlank
        @Size(max = 255)
        @JsonAlias({"agent_id", "agentId"})
        String agentId,
        @Size(max = 255)
        @JsonAlias({"device_fingerprint", "deviceFingerprint"})
        String deviceFingerprint,
        @Size(max = 255)
        @JsonAlias({"device_label", "deviceLabel"})
        String deviceLabel,
        @Size(max = 64)
        @JsonAlias({"tenant_id", "tenantId"})
        String tenantId
) {
}