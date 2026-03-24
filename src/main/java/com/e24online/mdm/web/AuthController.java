package com.e24online.mdm.web;

import com.e24online.mdm.domain.AuthRefreshToken;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.user.ChangePasswordRequest;
import com.e24online.mdm.records.user.LoginRequest;
import com.e24online.mdm.records.user.LoginResponse;
import com.e24online.mdm.repository.AuthRefreshTokenRepository;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.AuditEventService;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.ExpiredTokenRevocationService;
import com.e24online.mdm.service.LocalBreachedPasswordService;
import com.e24online.mdm.web.security.JwtService;
import com.e24online.mdm.records.user.UserPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    private static final String REFRESH_COOKIE = "REFRESH_TOKEN";

    private final AuthUserRepository userRepository;
    private final AuthRefreshTokenRepository refreshRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BlockingDb blockingDb;
    private final AuditEventService auditEventService;
    private final TenantRepository tenantRepository;
    private final ServerCsrfTokenRepository csrfTokenRepository;
    private final LocalBreachedPasswordService localBreachedPasswordService;
    private final ExpiredTokenRevocationService expiredTokenRevocationService;

    public AuthController(AuthUserRepository userRepository,
                          AuthRefreshTokenRepository refreshRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          BlockingDb blockingDb,
                          AuditEventService auditEventService,
                          TenantRepository tenantRepository,
                          ServerCsrfTokenRepository csrfTokenRepository,
                          LocalBreachedPasswordService localBreachedPasswordService,
                          ExpiredTokenRevocationService expiredTokenRevocationService) {
        this.userRepository = userRepository;
        this.refreshRepository = refreshRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.blockingDb = blockingDb;
        this.auditEventService = auditEventService;
        this.tenantRepository = tenantRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.localBreachedPasswordService = localBreachedPasswordService;
        this.expiredTokenRevocationService = expiredTokenRevocationService;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request,
                                                     ServerWebExchange exchange) {
        return blockingDb.mono(() -> {
            String requestedUsername = request == null ? null : normalizeOptionalUsername(request.username());
            if (request == null
                    || request.username() == null
                    || request.password() == null
                    || request.username().isBlank()
                    || request.password().isBlank()) {
                auditLoginEvent("FAILURE", requestedUsername, null, "INVALID_REQUEST");
                return ResponseEntity.badRequest().build();
            }

            String username = request.username().trim();
            Optional<AuthUser> userOpt = userRepository.findByUsernameAndIsDeletedFalse(username);
            if (userOpt.isEmpty()) {
                auditLoginEvent("FAILURE", username, null, "USER_NOT_FOUND");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            AuthUser user = userOpt.get();
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                auditLoginEvent("FAILURE", username, user, "USER_NOT_ACTIVE");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                auditLoginEvent("FAILURE", username, user, "BAD_CREDENTIALS");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(request.password()));
                user.setModifiedAt(OffsetDateTime.now());
                user.setModifiedBy("auth");
                userRepository.save(user);
            }

            // Check if password has been breached using LOCAL database (FREE, OFFLINE)
            // This checks against top 1000+ breached passwords + pattern detection
            boolean passwordBreached = localBreachedPasswordService.isBreached(request.password());
            
            // If password is breached, log a warning audit event
            if (passwordBreached) {
                auditLoginEvent("SUCCESS_BREACHED_PASSWORD", username, user, "PASSWORD_IN_BREACH_DATABASE");
            }

            String access = jwtService.generateAccessToken(user);
            OffsetDateTime now = OffsetDateTime.now();

            // Revoke any expired tokens for this user before creating/reusing a token
            expiredTokenRevocationService.revokeExpiredTokensForUser(user.getId());

            String refresh;
            Optional<AuthRefreshToken> existingActiveToken = findActiveRefreshToken(user.getId(), now);
            if (existingActiveToken.isPresent()) {
                refresh = jwtService.generateRefreshToken(user, existingActiveToken.get().getJti());
            } else {
                String jti = jwtService.newJti();
                refresh = jwtService.generateRefreshToken(user, jti);
                AuthRefreshToken art = new AuthRefreshToken();
                art.setUserId(user.getId());
                art.setJti(jti);
                art.setExpiresAt(now.plus(jwtService.refreshTtl()));
                art.setRevoked(false);
                art.setCreatedAt(now);
                art.setCreatedBy("auth");
                refreshRepository.save(art);
            }

            addCookies(exchange, access, refresh);

            LoginResponse body = new LoginResponse(user.getUsername(), user.getRole(), user.getTenantId(), passwordBreached);
            auditLoginEvent("SUCCESS", username, user, null);
            return ResponseEntity.ok(body);
        });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Void>> refresh(ServerWebExchange exchange) {
        return blockingDb.mono(() -> {
            var cookie = exchange.getRequest().getCookies().getFirst(REFRESH_COOKIE);
            if (cookie == null) {
                auditRefreshEvent("FAILURE", null, null, "anonymous", "MISSING_COOKIE");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String token = cookie.getValue();
            if (token.isBlank()) {
                auditRefreshEvent("FAILURE", null, null, "anonymous", "BLANK_COOKIE");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Claims claims;
            try {
                claims = jwtService.parseToken(token);
            } catch (Exception _) {
                auditRefreshEvent("FAILURE", null, null, "anonymous", "INVALID_TOKEN");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String actor = normalizeOptionalUsername(claims.getSubject());
            if (actor == null) {
                actor = "anonymous";
            }
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                auditRefreshEvent("FAILURE", null, null, actor, "MISSING_JTI");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Optional<AuthRefreshToken> storedOpt = refreshRepository.findByJti(jti);
            if (storedOpt.isEmpty()) {
                auditRefreshEvent("FAILURE", null, null, actor, "REFRESH_TOKEN_NOT_FOUND");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            AuthRefreshToken stored = storedOpt.get();
            if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
                auditRefreshEvent("FAILURE", null, stored.getUserId(), actor, "REFRESH_TOKEN_REVOKED_OR_EXPIRED");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Long userId = claims.get("uid", Long.class);
            AuthUser user = userRepository.findById(userId).orElse(null);
            if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus()) || user.isDeleted()) {
                auditRefreshEvent("FAILURE", null, userId, actor, "USER_NOT_ACTIVE");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String newAccess = jwtService.generateAccessToken(user);
            addAccessCookie(exchange, newAccess);
            auditRefreshEvent("SUCCESS", user.getTenantId(), user.getId(), user.getUsername(), null);
            return ResponseEntity.ok().build();
        });
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(org.springframework.security.core.Authentication authentication,
                                             ServerWebExchange exchange) {
        return blockingDb.mono(() -> {
            auditLogoutEvent(authentication);
            clearCookies(exchange);
            return ResponseEntity.noContent().build();
        });
    }

    @GetMapping("/me")
    public Mono<Map<String, Object>> me(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return Mono.just(Map.of("authenticated", false));
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("authenticated", true);
            body.put("userId", up.id());
            body.put("username", up.username());
            body.put("role", up.role());
            body.put("tenantId", up.tenantId());
            return Mono.just(body);
        }
        return Mono.just(Map.of("authenticated", false));
    }

    @GetMapping("/csrf")
    public Mono<Map<String, String>> csrf(ServerWebExchange exchange) {
        return csrfTokenRepository.generateToken(exchange)
                .flatMap(token -> csrfTokenRepository.saveToken(exchange, token)
                        .thenReturn(token))
                .map(token -> Map.of("token", token.getToken()));
    }

    @PostMapping("/change-password")
    public Mono<ResponseEntity<Map<String, String>>> changePassword(
            @RequestBody ChangePasswordRequest request,
            org.springframework.security.core.Authentication authentication,
            ServerWebExchange exchange
    ) {
        return blockingDb.mono(() -> {
            UserPrincipal principal = authentication != null && authentication.getPrincipal() instanceof UserPrincipal up
                    ? up
                    : null;
            if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal up)) {
                auditPasswordChange("FAILURE", principal, "UNAUTHORIZED");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            }
            if (request == null
                    || request.currentPassword() == null || request.currentPassword().isBlank()
                    || request.newPassword() == null || request.newPassword().isBlank()
                    || request.confirmPassword() == null || request.confirmPassword().isBlank()) {
                auditPasswordChange("FAILURE", up, "INVALID_REQUEST");
                return ResponseEntity.badRequest().body(Map.of("message", "All password fields are required"));
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                auditPasswordChange("FAILURE", up, "CONFIRM_MISMATCH");
                return ResponseEntity.badRequest().body(Map.of("message", "New password and confirmation do not match"));
            }
            if (!isStrongPassword(request.newPassword())) {
                auditPasswordChange("FAILURE", up, "WEAK_PASSWORD");
                return ResponseEntity.badRequest().body(Map.of(
                        "message",
                        "Password must be at least 12 characters and include upper, lower, number, and special character"
                ));
            }
            
            // Check if new password has been breached using LOCAL database (FREE)
            // This checks against top 1000+ breached passwords + pattern detection
            if (localBreachedPasswordService.isBreached(request.newPassword())) {
                auditPasswordChange("FAILURE", up, "BREACHED_PASSWORD");
                return ResponseEntity.badRequest().body(Map.of(
                        "message",
                        "This password has been exposed in data breaches or matches common weak patterns. Please choose a stronger password."
                ));
            }

            AuthUser user = userRepository.findById(up.id()).orElse(null);
            if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus()) || user.isDeleted()) {
                auditPasswordChange("FAILURE", up, "USER_NOT_ACTIVE");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User is not active"));
            }
            if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                auditPasswordChange("FAILURE", up, "BAD_CURRENT_PASSWORD");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Current password is incorrect"));
            }

            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            user.setTokenVersion((user.getTokenVersion() != null ? user.getTokenVersion() : 0L) + 1);
            user.setModifiedAt(OffsetDateTime.now());
            user.setModifiedBy(user.getUsername());
            userRepository.save(user);
            revokeAllRefreshTokens(user.getId());
            clearCookies(exchange);
            auditPasswordChange("SUCCESS", up, null);
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        });
    }

    private void addCookies(ServerWebExchange exchange, String access, String refresh) {
        addAccessCookie(exchange, access);
        addRefreshCookie(exchange, refresh);
    }

    private void addRefreshCookie(ServerWebExchange exchange, String refresh) {
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE, refresh)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(refreshTokenMaxAgeSeconds())
                .sameSite("Strict")
                .build();
        exchange.getResponse().addCookie(refreshCookie);
    }

    private void addAccessCookie(ServerWebExchange exchange, String access) {
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_COOKIE, access)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(accessTokenMaxAgeSeconds())
                .sameSite("Lax")
                .build();
        exchange.getResponse().addCookie(accessCookie);
    }

    private long accessTokenMaxAgeSeconds() {
        return Math.max(0L, jwtService.accessTtl().toSeconds());
    }

    private long refreshTokenMaxAgeSeconds() {
        return Math.max(0L, jwtService.refreshTtl().toSeconds());
    }

    private Optional<AuthRefreshToken> findActiveRefreshToken(Long userId, OffsetDateTime now) {
        if (userId == null) {
            return Optional.empty();
        }
        for (AuthRefreshToken token : refreshRepository.findByUserId(userId)) {
            if (!token.isRevoked() && token.getExpiresAt().isAfter(now)) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    private void revokeAllRefreshTokens(Long userId) {
        if (userId == null) {
            return;
        }
        for (AuthRefreshToken token : refreshRepository.findByUserId(userId)) {
            if (token.isRevoked()) {
                continue;
            }
            token.setRevoked(true);
            refreshRepository.save(token);
        }
    }

    private void clearCookies(ServerWebExchange exchange) {
        ResponseCookie expiredAccess = ResponseCookie.from(ACCESS_COOKIE, "")
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie expiredRefresh = ResponseCookie.from(REFRESH_COOKIE, "")
                .path("/")
                .maxAge(0)
                .build();
        exchange.getResponse().addCookie(expiredAccess);
        exchange.getResponse().addCookie(expiredRefresh);
    }

    private boolean isStrongPassword(String password) {
        if (password == null || password.length() < 12) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private String normalizeOptionalUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void auditLoginEvent(String status, String username, AuthUser user, String reason) {
        String actor = normalizeOptionalUsername(username);
        if (actor == null) {
            actor = "anonymous";
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("username", actor);
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        if (user != null) {
            metadata.put("role", user.getRole());
            metadata.put("tenantMasterId", user.getTenantId());
        }

        auditEventService.recordBestEffort(
                "AUTH",
                "USER_LOGIN",
                "LOGIN",
                user != null ? resolveTenantCode(user.getTenantId()) : null,
                actor,
                "AUTH_USER",
                user == null || user.getId() == null ? null : String.valueOf(user.getId()),
                status,
                metadata
        );
    }

    private void auditLogoutEvent(org.springframework.security.core.Authentication authentication) {
        UserPrincipal principal = authentication != null && authentication.getPrincipal() instanceof UserPrincipal up
                ? up
                : null;
        String actor = principal == null || principal.username() == null || principal.username().isBlank()
                ? "anonymous"
                : principal.username();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actorType", principal == null ? "ANONYMOUS" : "USER");

        auditEventService.recordBestEffort(
                "AUTH",
                "USER_LOGOUT",
                "LOGOUT",
                principal == null ? null : resolveTenantCode(principal.tenantId()),
                actor,
                "AUTH_USER",
                principal == null || principal.id() == null ? null : String.valueOf(principal.id()),
                "SUCCESS",
                metadata
        );
    }

    private void auditPasswordChange(String status, UserPrincipal principal, String reason) {
        String actor = principal == null || principal.username() == null || principal.username().isBlank()
                ? "anonymous"
                : principal.username();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditEventService.recordBestEffort(
                "AUTH",
                "PASSWORD_CHANGED",
                "CHANGE_PASSWORD",
                principal == null ? null : resolveTenantCode(principal.tenantId()),
                actor,
                "AUTH_USER",
                principal == null || principal.id() == null ? null : String.valueOf(principal.id()),
                status,
                metadata
        );
    }

    private void auditRefreshEvent(String status,
                                   Long tenantMasterId,
                                   Long userId,
                                   String actor,
                                   String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", userId);
        metadata.put("tenantMasterId", tenantMasterId);
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        String normalizedActor = normalizeOptionalUsername(actor);
        if (normalizedActor == null) {
            normalizedActor = "anonymous";
        }
        auditEventService.recordBestEffort(
                "AUTH",
                "TOKEN_REFRESH",
                "REFRESH",
                resolveTenantCode(tenantMasterId),
                normalizedActor,
                "AUTH_USER",
                userId == null ? null : String.valueOf(userId),
                status,
                metadata
        );
    }

    private String resolveTenantCode(Long tenantMasterId) {
        if (tenantMasterId == null) {
            return null;
        }
        return tenantRepository.findById(tenantMasterId)
                .map(Tenant::getTenantId)
                .map(this::normalizeOptionalUsername)
                .orElse(null);
    }
}
