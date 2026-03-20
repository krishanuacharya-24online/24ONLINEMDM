package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_posture_payload")
public class DevicePosturePayload {

    @Id
    private Long id;

    @Column("tenant_id")
    private String tenantId;

    @Column("device_external_id")
    private String deviceExternalId;

    @Column("agent_id")
    private String agentId;

    @Column("payload_version")
    private String payloadVersion;

    @Column("capture_time")
    private OffsetDateTime captureTime;

    @Column("agent_version")
    private String agentVersion;

    @Column("agent_capabilities")
    private String agentCapabilities;

    @Column("payload_hash")
    private String payloadHash;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("payload_json")
    private String payloadJson;

    @Column("schema_compatibility_status")
    private String schemaCompatibilityStatus;

    @Column("validation_warnings")
    private String validationWarnings;

    @Column("received_at")
    private OffsetDateTime receivedAt;

    @Column("process_status")
    private String processStatus;

    @Column("process_error")
    private String processError;

    @Column("processed_at")
    private OffsetDateTime processedAt;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

}
