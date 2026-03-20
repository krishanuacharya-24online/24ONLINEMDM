package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Table("tenant_usage_snapshot")
public class TenantUsageSnapshot {

    @Id
    private Long id;

    @Column("tenant_master_id")
    private Long tenantMasterId;

    @Column("usage_month")
    private LocalDate usageMonth;

    @Column("active_device_count")
    private Integer activeDeviceCount;

    @Column("active_user_count")
    private Integer activeUserCount;

    @Column("posture_payload_count")
    private Long posturePayloadCount;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}

