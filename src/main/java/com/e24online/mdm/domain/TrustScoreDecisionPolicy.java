package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("trust_score_decision_policy")
public class TrustScoreDecisionPolicy {

    @Id
    private Long id;

    @Column("policy_name")
    private String policyName;

    @Column("score_min")
    private Short scoreMin;

    @Column("score_max")
    private Short scoreMax;

    @Column("decision_action")
    private String decisionAction;

    @Column("remediation_required")
    private boolean remediationRequired;

    @Column("response_message")
    private String responseMessage;

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
