package com.e24online.mdm.records;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;

public record CreateSetupKeyRequest(
        @JsonAlias("max_uses")
        @Min(1)
        Integer maxUses,
        @JsonAlias({"target_user_id", "targetUserId"})
        Long targetUserId,
        @JsonAlias("ttl_minutes")
        @Min(1)
        Integer ttlMinutes
) {
}