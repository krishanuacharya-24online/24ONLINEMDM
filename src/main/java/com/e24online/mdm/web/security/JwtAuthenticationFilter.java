package com.e24online.mdm.web.security;

import com.e24online.mdm.config.ApiVersionConfig;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.TokenClaims;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.AuditEventService;
import com.e24online.mdm.service.BlockingDb;
import io.jsonwebtoken.Claims;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String ACCESS_COOKIE = "ACCESS_TOKEN";

    private final ApiVersionConfig apiVersionConfig;
    private final JwtService jwtService;
    private final AuthUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AuditEventService auditEventService;
    private final BlockingDb blockingDb;

    public JwtAuthenticationFilter(
            ApiVersionConfig apiVersionConfig,
            JwtService jwtService,
            AuthUserRepository userRepository,
            TenantRepository tenantRepository,
            AuditEventService auditEventService,
            BlockingDb blockingDb
    ) {
        this.apiVersionConfig = apiVersionConfig;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.auditEventService = auditEventService;
        this.blockingDb = blockingDb;
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Agent APIs must use tenant headers/key authentication, not JWT cookies.
        if (path.startsWith(apiVersionConfig.getPrefix() + "/agent/")) {
            return chain.filter(exchange);
        }

        HttpCookie cookie = request.getCookies().getFirst(ACCESS_COOKIE);
        if (cookie == null) {
            return chain.filter(exchange);
        }

        String token = cookie.getValue();
        if (token.isBlank()) {
            return chain.filter(exchange);
        }

        try {
            Claims claims = jwtService.parseToken(token);
            TokenClaims tokenClaims = extractTokenClaims(claims);
            if (!tokenClaims.isValid()) {
                log.warn("Invalid JWT claims for path: {}", path);
                return auditTokenFailure(path, tokenClaims, null, "INVALID_CLAIMS")
                        .then(chain.filter(exchange));
            }

            // Run blocking JDBC lookup on dedicated scheduler so Netty event-loop threads never block.
            return blockingDb.mono(() -> userRepository.findActiveById(tokenClaims.userId()))
                    .flatMap(userOpt -> authenticateIfValid(exchange, chain, tokenClaims, userOpt))
                    .onErrorResume(e -> {
                        log.error("Unexpected error processing JWT for path: {}", path, e);
                        return auditTokenFailure(path, tokenClaims, null, "UNEXPECTED_ERROR")
                                .then(chain.filter(exchange));
                    });
        } catch (io.jsonwebtoken.ExpiredJwtException _) {
            log.debug("Expired JWT token for path: {}", path);
            return auditTokenFailure(path, null, null, "TOKEN_EXPIRED")
                    .then(chain.filter(exchange));
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Invalid JWT token for path: {}: {}", path, e.getMessage());
            return auditTokenFailure(path, null, null, "INVALID_TOKEN")
                    .then(chain.filter(exchange));
        }
    }

    private Mono<Void> authenticateIfValid(ServerWebExchange exchange,
                                           WebFilterChain chain,
                                           TokenClaims tokenClaims,
                                           Optional<AuthUser> userOpt) {
        if (userOpt.isEmpty()) {
            log.warn("Invalid token: user {} (id={}) not found or not active", tokenClaims.username(), tokenClaims.userId());
            return auditTokenFailure(exchange.getRequest().getPath().value(), tokenClaims, null, "USER_NOT_FOUND_OR_INACTIVE")
                    .then(chain.filter(exchange));
        }

        AuthUser user = userOpt.get();

        // Validate token version matches (detects revoked/invalidated tokens)
        Long currentUserVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0L;
        if (!currentUserVersion.equals(tokenClaims.tokenVersion())) {
            log.warn("Invalid token version: user {} (id={}) has version {}, but token has version {}",
                    tokenClaims.username(), tokenClaims.userId(), currentUserVersion, tokenClaims.tokenVersion());
            return auditTokenFailure(exchange.getRequest().getPath().value(), tokenClaims, user, "TOKEN_VERSION_MISMATCH")
                    .then(chain.filter(exchange));
        }

        // Additional status check (defensive, in case findActiveById doesn't catch all cases)
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus()) || user.isDeleted()) {
            log.warn("Token rejected: user {} (id={}) status={}, deleted={}",
                    tokenClaims.username(), tokenClaims.userId(), user.getStatus(), user.isDeleted());
            return auditTokenFailure(exchange.getRequest().getPath().value(), tokenClaims, user, "USER_STATUS_REJECTED")
                    .then(chain.filter(exchange));
        }

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + tokenClaims.role());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(tokenClaims.userId(), tokenClaims.username(), tokenClaims.role(), tokenClaims.tenantId()),
                null,
                List.of(authority)
        );

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    private TokenClaims extractTokenClaims(Claims claims) {
        return new TokenClaims(
                claims.getSubject(),
                claims.get("role", String.class),
                claims.get("uid", Long.class),
                claims.get("tenantId", Long.class),
                claims.get("tokenVersion", Long.class)
        );
    }

    private Mono<Void> auditTokenFailure(String path, TokenClaims tokenClaims, AuthUser user, String reason) {
        return blockingDb.run(() -> {
            String actor = tokenClaims != null && tokenClaims.username() != null && !tokenClaims.username().isBlank()
                    ? tokenClaims.username().trim()
                    : "anonymous";
            Long tokenUserId = tokenClaims == null ? null : tokenClaims.userId();
            Long tenantMasterId = user != null ? user.getTenantId() : (tokenClaims == null ? null : tokenClaims.tenantId());

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("path", path);
            metadata.put("reason", reason);
            if (tokenClaims != null) {
                metadata.put("tokenUserId", tokenClaims.userId());
                metadata.put("tokenRole", tokenClaims.role());
                metadata.put("tokenVersion", tokenClaims.tokenVersion());
                metadata.put("tokenTenantMasterId", tokenClaims.tenantId());
            }
            if (user != null) {
                metadata.put("dbUserId", user.getId());
                metadata.put("dbTokenVersion", user.getTokenVersion());
                metadata.put("dbStatus", user.getStatus());
            }

            auditEventService.recordBestEffort(
                    "AUTH",
                    "JWT_AUTHENTICATION",
                    "VALIDATE_TOKEN",
                    resolveTenantCode(tenantMasterId),
                    actor,
                    "AUTH_USER",
                    tokenUserId == null ? null : String.valueOf(tokenUserId),
                    "FAILURE",
                    metadata
            );
        }).onErrorResume(_ -> Mono.empty());
    }

    private String resolveTenantCode(Long tenantMasterId) {
        if (tenantMasterId == null) {
            return null;
        }
        return tenantRepository.findById(tenantMasterId)
                .map(Tenant::getTenantId)
                .orElse(null);
    }

}
