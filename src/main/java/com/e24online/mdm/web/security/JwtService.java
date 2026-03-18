package com.e24online.mdm.web.security;

import com.e24online.mdm.domain.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final javax.crypto.SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(
            @Value("${security.jwt.secret:change-me-please-change-me-please-change-me-please}") String secret,
            @Value("${security.jwt.access-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${security.jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 characters for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
        this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
    }

    public String generateAccessToken(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .claim("tenantId", user.getTenantId())
                .claim("tokenVersion", user.getTokenVersion() != null ? user.getTokenVersion() : 0)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(AuthUser user, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .claim("uid", user.getId())
                .signWith(key)
                .compact();
    }

    public String newJti() {
        return UUID.randomUUID().toString();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }
}
