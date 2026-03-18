package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@Table("audit_event_log")
public class AuditEventLog {

    @Id
    private Long id;

    @Column("event_id")
    private String eventId;

    @Column("event_category")
    private String eventCategory;

    @Column("event_type")
    private String eventType;

    @Column("action")
    private String action;

    @Column("tenant_id")
    private String tenantId;

    @Column("actor")
    private String actor;

    @Column("entity_type")
    private String entityType;

    @Column("entity_id")
    private String entityId;

    @Column("status")
    private String status;

    @Column("metadata_json")
    private String metadataJson;

    @Column("created_at")
    private OffsetDateTime createdAt;
}

