package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("posture_evaluation_remediation")
public class PostureEvaluationRemediation {

    @Id
    private Long id;

    @Column("posture_evaluation_run_id")
    private Long postureEvaluationRunId;

    @Column("remediation_rule_id")
    private Long remediationRuleId;

    @Column("posture_evaluation_match_id")
    private Long postureEvaluationMatchId;

    @Column("source_type")
    private String sourceType;

    @Column("remediation_status")
    private String remediationStatus;

    @Column("due_at")
    private OffsetDateTime dueAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;

    @Column("instruction_override")
    private String instructionOverride;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

}

