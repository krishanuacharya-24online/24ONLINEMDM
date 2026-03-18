package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("posture_evaluation_match")
public class PostureEvaluationMatch {

    @Id
    private Long id;

    @Column("posture_evaluation_run_id")
    private Long postureEvaluationRunId;

    @Column("match_source")
    private String matchSource;

    @Column("system_information_rule_id")
    private Long systemInformationRuleId;

    @Column("reject_application_list_id")
    private Long rejectApplicationListId;

    @Column("trust_score_policy_id")
    private Long trustScorePolicyId;

    @Column("os_release_lifecycle_master_id")
    private Long osReleaseLifecycleMasterId;

    @Column("os_lifecycle_state")
    private String osLifecycleState;

    @Column("device_installed_application_id")
    private Long deviceInstalledApplicationId;

    @Column("remediation_rule_id")
    private Long remediationRuleId;

    @Column("matched")
    private boolean matched;

    @Column("severity")
    private Short severity;

    @Column("compliance_action")
    private String complianceAction;

    @Column("score_delta")
    private Short scoreDelta;

    @Column("match_detail")
    private String matchDetail;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

}

