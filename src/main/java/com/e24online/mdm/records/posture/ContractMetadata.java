package com.e24online.mdm.records.posture;

import java.time.OffsetDateTime;
import java.util.List;

public record ContractMetadata(
        OffsetDateTime captureTime,
        String agentCapabilitiesJson,
        String schemaCompatibilityStatus,
        List<String> validationWarnings
) {
}
