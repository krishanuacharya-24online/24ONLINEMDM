package com.e24online.mdm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class PolicyAuditMessage {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    @NotNull
    private Integer schemaVersion = CURRENT_SCHEMA_VERSION;

    @NotBlank
    private String eventId;

    @NotBlank
    private String policyType;

    private Long policyId;

    @NotBlank
    @Pattern(regexp = "CREATE|UPDATE|DELETE|CLONE", message = "must be one of CREATE, UPDATE, DELETE, CLONE")
    private String operation;

    private String tenantId;

    @NotBlank
    private String actor;

    private String approvalTicket;

    private String beforeStateJson;

    private String afterStateJson;

    @NotNull
    private OffsetDateTime createdAt;
}
