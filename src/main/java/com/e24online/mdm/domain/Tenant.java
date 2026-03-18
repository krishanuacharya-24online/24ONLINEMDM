package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("tenant_master")
public class Tenant {

    @Id
    private Long id;

    private String tenantId;
    private String name;
    private String status;

    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime modifiedAt;
    private String modifiedBy;
    private boolean isDeleted;

}

