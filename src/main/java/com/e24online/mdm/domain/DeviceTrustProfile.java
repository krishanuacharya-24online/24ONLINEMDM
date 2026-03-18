package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_trust_profile")
public class DeviceTrustProfile {

    @Id
    private Long id;

    @Column("tenant_id")
    private String tenantId;

    @Column("device_external_id")
    private String deviceExternalId;

    @Column("device_type")
    private String deviceType;

    @Column("os_type")
    private String osType;

    @Column("os_name")
    private String osName;

    @Column("os_lifecycle_state")
    private String osLifecycleState;

    @Column("os_release_lifecycle_master_id")
    private Long osReleaseLifecycleMasterId;

    @Column("current_score")
    private Short currentScore;

    @Column("score_band")
    private String scoreBand;

    @Column("posture_status")
    private String postureStatus;

    @Column("last_event_at")
    private OffsetDateTime lastEventAt;

    @Column("last_recalculated_at")
    private OffsetDateTime lastRecalculatedAt;

    @Column("is_deleted")
    private boolean deleted;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}
