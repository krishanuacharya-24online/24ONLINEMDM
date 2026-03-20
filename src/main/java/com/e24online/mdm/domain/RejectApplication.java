package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("reject_application_list")
public class RejectApplication {

    @Id
    private Long id;

    @Column("policy_tag")
    private String policyTag;

    @Column("severity")
    private Short severity;

    @Column("app_name")
    private String appName;

    @Column("publisher")
    private String publisher;

    @Column("package_id")
    private String packageId;

    @Column("app_os_type")
    private String appOsType;

    @Column("min_allowed_version")
    private String minAllowedVersion;

    @Column("tenant_id")
    private String tenantId;

    @Column("status")
    private String status;

    @Column("effective_from")
    private OffsetDateTime effectiveFrom;

    @Column("effective_to")
    private OffsetDateTime effectiveTo;

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
