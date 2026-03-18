package com.e24online.mdm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AuditEventMessage {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    @NotNull
    private Integer schemaVersion = CURRENT_SCHEMA_VERSION;

    @NotBlank
    @Size(max = 255)
    private String eventId;

    @NotBlank
    @Size(max = 128)
    private String eventCategory;

    @NotBlank
    @Size(max = 128)
    private String eventType;

    @Size(max = 128)
    private String action;

    @Size(max = 255)
    private String tenantId;

    @NotBlank
    @Size(max = 255)
    private String actor;

    @Size(max = 128)
    private String entityType;

    @Size(max = 255)
    private String entityId;

    @NotBlank
    @Size(max = 32)
    private String status;

    private String metadataJson;

    @NotNull
    private OffsetDateTime createdAt;
}

