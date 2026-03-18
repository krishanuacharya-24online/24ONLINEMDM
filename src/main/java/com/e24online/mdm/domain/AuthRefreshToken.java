package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("auth_refresh_token")
public class AuthRefreshToken {

    @Id
    private Long id;

    private Long userId;
    private String jti;
    private OffsetDateTime expiresAt;
    private boolean revoked;
    private OffsetDateTime createdAt;
    private String createdBy;

}

