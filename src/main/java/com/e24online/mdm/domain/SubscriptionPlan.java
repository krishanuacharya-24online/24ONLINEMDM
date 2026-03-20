package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@Table("subscription_plan")
public class SubscriptionPlan {

    @Id
    private Long id;

    @Column("plan_code")
    private String planCode;

    @Column("plan_name")
    private String planName;

    private String description;

    @Column("max_active_devices")
    private Integer maxActiveDevices;

    @Column("max_tenant_users")
    private Integer maxTenantUsers;

    @Column("max_monthly_payloads")
    private Long maxMonthlyPayloads;

    @Column("data_retention_days")
    private Integer dataRetentionDays;

    @Column("premium_reporting_enabled")
    private boolean premiumReportingEnabled;

    @Column("advanced_controls_enabled")
    private boolean advancedControlsEnabled;

    private String status;

    @Column("is_deleted")
    private boolean isDeleted;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}

