package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("tenant_api_key")
public class TenantApiKey {

    @Id
    private Long id;

    private Long tenantMasterId;
    private String keyHash;
    private String keyHint;
    private String status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime revokedAt;
    private String revokedBy;

}
