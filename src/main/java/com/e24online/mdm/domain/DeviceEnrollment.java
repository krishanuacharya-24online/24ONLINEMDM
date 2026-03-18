package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_enrollment")
public class DeviceEnrollment {

    @Id
    private Long id;

    @Column("tenant_id")
    private String tenantId;

    @Column("enrollment_no")
    private String enrollmentNo;

    @Column("enrollment_method")
    private String enrollmentMethod;

    @Column("status")
    private String status;

    @Column("agent_id")
    private String agentId;

    @Column("device_label")
    private String deviceLabel;

    @Column("device_fingerprint")
    private String deviceFingerprint;

    @Column("setup_key_id")
    private Long setupKeyId;

    @Column("owner_user_id")
    private Long ownerUserId;

    @Column("enrolled_at")
    private OffsetDateTime enrolledAt;

    @Column("de_enrolled_at")
    private OffsetDateTime deEnrolledAt;

    @Column("de_enroll_reason")
    private String deEnrollReason;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}
