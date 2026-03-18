package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("trust_score_policy")
public class TrustScorePolicy {

    @Id
    private Long id;

    @Column("policy_code")
    private String policyCode;

    @Column("source_type")
    private String sourceType;

    @Column("signal_key")
    private String signalKey;

    @Column("severity")
    private Short severity;

    @Column("compliance_action")
    private String complianceAction;

    @Column("score_delta")
    private Short scoreDelta;

    @Column("weight")
    private Double weight;

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
