package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("posture_evaluation_run")
public class PostureEvaluationRun {

    @Id
    private Long id;

    @Column("device_posture_payload_id")
    private Long devicePosturePayloadId;

    @Column("device_trust_profile_id")
    private Long deviceTrustProfileId;

    @Column("trust_score_decision_policy_id")
    private Long trustScoreDecisionPolicyId;

    @Column("os_release_lifecycle_master_id")
    private Long osReleaseLifecycleMasterId;

    @Column("os_lifecycle_state")
    private String osLifecycleState;

    @Column("evaluation_status")
    private String evaluationStatus;

    @Column("trust_score_before")
    private Short trustScoreBefore;

    @Column("trust_score_delta_total")
    private Short trustScoreDeltaTotal;

    @Column("trust_score_after")
    private Short trustScoreAfter;

    @Column("decision_action")
    private String decisionAction;

    @Column("decision_reason")
    private String decisionReason;

    @Column("remediation_required")
    private boolean remediationRequired;

    @Column("matched_rule_count")
    private Integer matchedRuleCount;

    @Column("matched_app_count")
    private Integer matchedAppCount;

    @Column("evaluated_at")
    private OffsetDateTime evaluatedAt;

    @Column("responded_at")
    private OffsetDateTime respondedAt;

    @Column("response_payload")
    private String responsePayload;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

}

