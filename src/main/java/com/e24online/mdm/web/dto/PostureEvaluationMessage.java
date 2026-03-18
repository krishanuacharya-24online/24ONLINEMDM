package com.e24online.mdm.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostureEvaluationMessage {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    @NotNull
    @Min(1)
    private Integer schemaVersion;

    @NotBlank
    @Size(max = 255)
    private String eventId;

    @NotBlank
    @Size(max = 255)
    private String tenantId;

    @NotNull
    @Min(1)
    private Long payloadId;

    @NotBlank
    @Size(max = 255)
    private String deviceExternalId;

    @NotBlank
    @Size(max = 512)
    private String payloadHash;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;

    @NotNull
    private OffsetDateTime queuedAt;
}
