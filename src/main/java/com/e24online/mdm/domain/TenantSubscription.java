package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@Table("tenant_subscription")
public class TenantSubscription {

    @Id
    private Long id;

    @Column("tenant_master_id")
    private Long tenantMasterId;

    @Column("subscription_plan_id")
    private Long subscriptionPlanId;

    @Column("subscription_state")
    private String subscriptionState;

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("current_period_start")
    private OffsetDateTime currentPeriodStart;

    @Column("current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Column("grace_ends_at")
    private OffsetDateTime graceEndsAt;

    @Column("suspended_at")
    private OffsetDateTime suspendedAt;

    @Column("cancelled_at")
    private OffsetDateTime cancelledAt;

    private String notes;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}

