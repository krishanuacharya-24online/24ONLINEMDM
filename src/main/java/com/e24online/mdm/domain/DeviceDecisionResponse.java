package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_decision_response")
public class DeviceDecisionResponse {

    @Id
    private Long id;

    @Column("posture_evaluation_run_id")
    private Long postureEvaluationRunId;

    @Column("tenant_id")
    private String tenantId;

    @Column("device_external_id")
    private String deviceExternalId;

    @Column("decision_action")
    private String decisionAction;

    @Column("trust_score")
    private Short trustScore;

    @Column("remediation_required")
    private boolean remediationRequired;

    @Column("response_payload")
    private String responsePayload;

    @Column("delivery_status")
    private String deliveryStatus;

    @Column("sent_at")
    private OffsetDateTime sentAt;

    @Column("acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

}

