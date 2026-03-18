package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_agent_credential")
public class DeviceAgentCredential {

    @Id
    private Long id;

    @Column("tenant_id")
    private String tenantId;

    @Column("device_enrollment_id")
    private Long deviceEnrollmentId;

    @Column("token_hash")
    private String tokenHash;

    @Column("token_hint")
    private String tokenHint;

    @Column("status")
    private String status;

    @Column("expires_at")
    private OffsetDateTime expiresAt;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("revoked_at")
    private OffsetDateTime revokedAt;

    @Column("revoked_by")
    private String revokedBy;

}
