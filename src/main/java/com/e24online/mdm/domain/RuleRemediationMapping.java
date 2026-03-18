package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("rule_remediation_mapping")
public class RuleRemediationMapping {

    @Id
    private Long id;

    @Column("source_type")
    private String sourceType;

    @Column("system_information_rule_id")
    private Long systemInformationRuleId;

    @Column("reject_application_list_id")
    private Long rejectApplicationListId;

    @Column("trust_score_policy_id")
    private Long trustScorePolicyId;

    @Column("decision_action")
    private String decisionAction;

    @Column("remediation_rule_id")
    private Long remediationRuleId;

    @Column("enforce_mode")
    private String enforceMode;

    @Column("rank_order")
    private Short rankOrder;

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
