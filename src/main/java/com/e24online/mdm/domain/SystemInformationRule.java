package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("system_information_rule")
public class SystemInformationRule {

    @Id
    private Long id;

    @Column("rule_code")
    private String ruleCode;

    @Column("priority")
    private Integer priority;

    @Column("version")
    private Integer version;

    @Column("match_mode")
    private String matchMode;

    @Column("compliance_action")
    private String complianceAction;

    @Column("risk_score_delta")
    private Short riskScoreDelta;

    @Column("rule_tag")
    private String ruleTag;

    @Column("tenant_id")
    private String tenantId;

    @Column("status")
    private String status;

    @Column("severity")
    private Short severity;

    @Column("description")
    private String description;

    @Column("device_type")
    private String deviceType;

    @Column("os_type")
    private String osType;

    @Column("os_name")
    private String osName;

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
