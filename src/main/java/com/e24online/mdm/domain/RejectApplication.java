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

    @Column("threat_type")
    private String threatType;

    @Column("severity")
    private Short severity;

    @Column("blocked_reason")
    private String blockedReason;

    @Column("app_name")
    private String appName;

    @Column("publisher")
    private String publisher;

    @Column("package_id")
    private String packageId;

    @Column("app_category")
    private String appCategory;

    @Column("app_os_type")
    private String appOsType;

    @Column("app_latest_version")
    private String appLatestVersion;

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
