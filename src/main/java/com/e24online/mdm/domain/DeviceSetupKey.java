package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_setup_key")
public class DeviceSetupKey {

    @Id
    private Long id;

    @Column("tenant_id")
    private String tenantId;

    @Column("key_hash")
    private String keyHash;

    @Column("key_hint")
    private String keyHint;

    @Column("status")
    private String status;

    @Column("max_uses")
    private Integer maxUses;

    @Column("used_count")
    private Integer usedCount;

    @Column("expires_at")
    private OffsetDateTime expiresAt;

    @Column("issued_by_user_id")
    private Long issuedByUserId;

    @Column("target_user_id")
    private Long targetUserId;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}
