package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("policy_change_audit")
public class PolicyChangeAudit {

    @Id
    private Long id;

    @Column("policy_type")
    private String policyType;

    @Column("policy_id")
    private Long policyId;

    @Column("operation")
    private String operation;

    @Column("tenant_id")
    private String tenantId;

    @Column("actor")
    private String actor;

    @Column("approval_ticket")
    private String approvalTicket;

    @Column("before_state_json")
    private String beforeStateJson;

    @Column("after_state_json")
    private String afterStateJson;

    @Column("created_at")
    private OffsetDateTime createdAt;
}

