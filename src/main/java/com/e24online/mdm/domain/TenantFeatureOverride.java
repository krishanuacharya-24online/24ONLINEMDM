package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@Table("tenant_feature_override")
public class TenantFeatureOverride {

    @Id
    private Long id;

    @Column("tenant_master_id")
    private Long tenantMasterId;

    @Column("feature_key")
    private String featureKey;

    private boolean enabled;

    @Column("expires_at")
    private OffsetDateTime expiresAt;

    private String reason;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}

