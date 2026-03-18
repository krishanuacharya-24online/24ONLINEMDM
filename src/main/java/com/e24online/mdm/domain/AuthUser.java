package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("auth_user")
public class AuthUser {

    @Id
    private Long id;

    private String username;
    private String passwordHash;
    private String role;
    private Long tenantId;
    private String status;

    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime modifiedAt;
    private String modifiedBy;
    private boolean isDeleted;
    private Long tokenVersion;

}

