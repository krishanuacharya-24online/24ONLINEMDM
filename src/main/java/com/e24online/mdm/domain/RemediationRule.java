package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("remediation_rule")
public class RemediationRule {

    @Id
    private Long id;

    @Column("remediation_code")
    private String remediationCode;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("remediation_type")
    private String remediationType;

    @Column("os_type")
    private String osType;

    @Column("device_type")
    private String deviceType;

    @Column("instruction_json")
    private String instructionJson;

    @Column("priority")
    private Short priority;

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
