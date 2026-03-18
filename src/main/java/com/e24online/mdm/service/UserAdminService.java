package com.e24online.mdm.service;

import com.e24online.mdm.domain.AuthRefreshToken;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.AccessScope;
import com.e24online.mdm.records.user.UserResponse;
import com.e24online.mdm.repository.AuthRefreshTokenRepository;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.service.LocalBreachedPasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserAdminService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,64}$");
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private final AuthUserRepository authUserRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final BlockingDb blockingDb;
    private final AuditEventService auditEventService;
    private final LocalBreachedPasswordService localBreachedPasswordService;

    public UserAdminService(AuthUserRepository authUserRepository,
                            AuthRefreshTokenRepository authRefreshTokenRepository,
                            TenantRepository tenantRepository,
                            PasswordEncoder passwordEncoder,
                            BlockingDb blockingDb,
                            AuditEventService auditEventService,
                            LocalBreachedPasswordService localBreachedPasswordService) {
        this.authUserRepository = authUserRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.blockingDb = blockingDb;
        this.auditEventService = auditEventService;
        this.localBreachedPasswordService = localBreachedPasswordService;
    }

    public Mono<UserResponse> createUser(UserPrincipal actorPrincipal,
                                         String username,
                                         String rawPassword,
                                         String role,
                                         String status,
                                         String tenantCode) {
        return blockingDb.mono(() -> {
            AccessScope actorScope = resolveActorScope(actorPrincipal);
            String normalizedUsername = normalizeUsername(username);
            String normalizedRole = normalizeRole(role);
            String normalizedStatus = normalizeStatus(status);
            String password = normalizePassword(rawPassword, true);
            String effectiveActor = actorScope.actor();

            if (authUserRepository.findByUsernameAndIsDeletedFalse(normalizedUsername).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
            }
            enforceRoleAssignment(actorScope, normalizedRole);
            Long tenantId = resolveTenantForRole(normalizedRole, tenantCode, actorScope);
            
            // Check if password has been breached using LOCAL database (FREE, OFFLINE)
            // This checks against top 1000+ breached passwords + pattern detection
            if (localBreachedPasswordService.isBreached(password)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Password has been exposed in data breaches or matches common weak patterns. Please choose a stronger password.");
            }

            AuthUser user = new AuthUser();
            OffsetDateTime now = OffsetDateTime.now();
            user.setUsername(normalizedUsername);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(normalizedRole);
            user.setTenantId(tenantId);
            user.setStatus(normalizedStatus);
            user.setTokenVersion(0L);
            user.setDeleted(false);
            user.setCreatedAt(now);
            user.setCreatedBy(effectiveActor);
            user.setModifiedAt(now);
            user.setModifiedBy(effectiveActor);
            AuthUser saved = authUserRepository.save(user);

            String mappedTenantCode = resolveTenantCode(tenantId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("username", saved.getUsername());
            metadata.put("role", saved.getRole());
            metadata.put("status", saved.getStatus());
            metadata.put("tenantId", mappedTenantCode);
            recordAudit("USER_CREATED", "CREATE", mappedTenantCode, effectiveActor, saved.getId(), metadata);
            return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole(), saved.getStatus(), mappedTenantCode);
        });
    }

    public Flux<UserResponse> listUsers(UserPrincipal actorPrincipal,
                                        String role,
                                        String status,
                                        String tenantCode,
                                        int page,
                                        int size) {
        return blockingDb.flux(() -> {
            AccessScope actorScope = resolveActorScope(actorPrincipal);
            String normalizedRoleFilter = normalizeOptionalRoleFilter(role);
            if (actorScope.tenantAdmin() && normalizedRoleFilter == null) {
                normalizedRoleFilter = "TENANT_USER";
            }
            String normalizedStatusFilter = normalizeOptionalStatusFilter(status);
            Long tenantFilter = resolveTenantForList(actorScope, tenantCode, normalizedRoleFilter);
            int safeSize = normalizePageSize(size);
            int safePage = normalizePage(page);
            long offset = (long) safePage * safeSize;

            List<UserResponse> rows = authUserRepository.findPaged(
                    normalizedRoleFilter,
                    normalizedStatusFilter,
                    tenantFilter,
                    safeSize,
                    offset
            ).stream().map(this::toUserResponse).toList();

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("roleFilter", normalizedRoleFilter);
            metadata.put("statusFilter", normalizedStatusFilter);
            metadata.put("tenantFilter", resolveTenantCode(tenantFilter));
            metadata.put("page", safePage);
            metadata.put("size", safeSize);
            metadata.put("resultCount", rows.size());
            recordAudit(
                    "USER_LIST_VIEWED",
                    "LIST",
                    resolveTenantCode(tenantFilter),
                    actorScope.actor(),
                    null,
                    metadata
            );
            return rows;
        });
    }

    public Mono<UserResponse> getUser(Long id, UserPrincipal actorPrincipal) {
        return blockingDb.mono(() -> {
            AccessScope actorScope = resolveActorScope(actorPrincipal);
            AuthUser existing = authUserRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            if (existing.isDeleted()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            enforceTargetUserAccess(actorScope, existing);
            UserResponse response = toUserResponse(existing);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("username", response.username());
            metadata.put("role", response.role());
            metadata.put("status", response.status());
            metadata.put("tenantId", response.tenantId());
            recordAudit(
                    "USER_VIEWED",
                    "VIEW",
                    response.tenantId(),
                    actorScope.actor(),
                    response.id(),
                    metadata
            );
            return response;
        });
    }

    public Mono<UserResponse> updateUser(Long id,
                                         UserPrincipal actorPrincipal,
                                         String role,
                                         String status,
                                         String tenantCode,
                                         String newPassword) {
        return blockingDb.mono(() -> {
            AccessScope actorScope = resolveActorScope(actorPrincipal);
            AuthUser existing = authUserRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            if (existing.isDeleted()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            enforceTargetUserAccess(actorScope, existing);

            String normalizedRole = normalizeRole(role);
            enforceRoleAssignment(actorScope, normalizedRole);
            String normalizedStatus = normalizeStatus(status);
            Long tenantId = resolveTenantForRole(normalizedRole, tenantCode, actorScope);

            String originalRole = existing.getRole();
            String originalStatus = existing.getStatus();
            Long originalTenantId = existing.getTenantId();
            String beforeTenantCode = resolveTenantCode(originalTenantId);
            existing.setRole(normalizedRole);
            existing.setStatus(normalizedStatus);
            existing.setTenantId(tenantId);
            String password = normalizePassword(newPassword, false);
            boolean passwordChanged = password != null;
            if (password != null) {
                existing.setPasswordHash(passwordEncoder.encode(password));
            }
            if (requiresSessionInvalidation(originalRole, normalizedRole,
                    originalStatus, normalizedStatus,
                    originalTenantId, tenantId,
                    passwordChanged)) {
                existing.setTokenVersion(nextTokenVersion(existing.getTokenVersion()));
                revokeAllRefreshTokens(existing.getId());
            }

            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(actorScope.actor());
            AuthUser saved = authUserRepository.save(existing);
            String mappedTenantCode = resolveTenantCode(tenantId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("username", saved.getUsername());
            metadata.put("beforeRole", originalRole);
            metadata.put("afterRole", saved.getRole());
            metadata.put("beforeStatus", originalStatus);
            metadata.put("afterStatus", saved.getStatus());
            metadata.put("beforeTenantId", beforeTenantCode);
            metadata.put("afterTenantId", mappedTenantCode);
            metadata.put("passwordChanged", passwordChanged);
            recordAudit("USER_UPDATED", "UPDATE", mappedTenantCode, actorScope.actor(), saved.getId(), metadata);
            return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole(), saved.getStatus(), mappedTenantCode);
        });
    }

    public Mono<Void> deleteUser(Long id, UserPrincipal actorPrincipal) {
        return blockingDb.run(() -> {
            AccessScope actorScope = resolveActorScope(actorPrincipal);
            AuthUser existing = authUserRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            if (existing.isDeleted()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            enforceTargetUserAccess(actorScope, existing);

            existing.setDeleted(true);
            existing.setStatus("INACTIVE");
            existing.setTokenVersion(nextTokenVersion(existing.getTokenVersion()));
            revokeAllRefreshTokens(existing.getId());
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(actorScope.actor());
            authUserRepository.save(existing);

            String mappedTenantCode = resolveTenantCode(existing.getTenantId());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("username", existing.getUsername());
            metadata.put("role", existing.getRole());
            metadata.put("status", existing.getStatus());
            metadata.put("tenantId", mappedTenantCode);
            recordAudit("USER_DELETED", "DELETE", mappedTenantCode, actorScope.actor(), existing.getId(), metadata);
        });
    }

    private boolean requiresSessionInvalidation(String originalRole,
                                                String newRole,
                                                String originalStatus,
                                                String newStatus,
                                                Long originalTenantId,
                                                Long newTenantId,
                                                boolean passwordChanged) {
        return passwordChanged
                || equalsIgnoreCase(originalRole, newRole)
                || equalsIgnoreCase(originalStatus, newStatus)
                || !Objects.equals(originalTenantId, newTenantId);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return !Objects.equals(left, right);
        }
        return !left.equalsIgnoreCase(right);
    }

    private long nextTokenVersion(Long currentVersion) {
        return (currentVersion == null ? 0L : currentVersion) + 1L;
    }

    private void revokeAllRefreshTokens(Long userId) {
        if (userId == null) {
            return;
        }
        for (AuthRefreshToken token : authRefreshTokenRepository.findByUserId(userId)) {
            if (token.isRevoked()) {
                continue;
            }
            token.setRevoked(true);
            authRefreshTokenRepository.save(token);
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        String normalized = username.trim();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username format is invalid");
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!"PRODUCT_ADMIN".equals(normalized)
                && !"TENANT_ADMIN".equals(normalized)
                && !"AUDITOR".equals(normalized)
                && !"TENANT_USER".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be PRODUCT_ADMIN, TENANT_ADMIN, AUDITOR, or TENANT_USER");
        }
        return normalized;
    }

    private String normalizeOptionalRoleFilter(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return normalizeRole(role);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or INACTIVE");
        }
        return normalized;
    }

    private String normalizeOptionalStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeStatus(status);
    }

    private String normalizePassword(String password, boolean required) {
        if (password == null || password.isBlank()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
            }
            return null;
        }
        String normalized = password.trim();
        if (!isStrongPassword(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "password must be at least 12 chars with upper, lower, number, special"
            );
        }
        return normalized;
    }

    private Long resolveTenantForRole(String role, String tenantCode, AccessScope actorScope) {
        if ("PRODUCT_ADMIN".equals(role)) {
            if (!actorScope.productAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only PRODUCT_ADMIN can assign PRODUCT_ADMIN role");
            }
            return null;
        }

        if (actorScope.productAdmin()) {
            return resolveTenantIdFromCode(tenantCode, role);
        }
        if (!actorScope.tenantAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only PRODUCT_ADMIN or TENANT_ADMIN can manage users");
        }

        Long actorTenantId = actorScope.tenantId();
        if (actorTenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN tenant scope is missing");
        }
        Long requestedTenantId = resolveOptionalTenantIdFromCode(tenantCode);
        if (requestedTenantId != null && !actorTenantId.equals(requestedTenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN can only manage own tenant");
        }
        return actorTenantId;
    }

    private void enforceRoleAssignment(AccessScope actorScope, String targetRole) {
        if (actorScope.productAdmin()) {
            return;
        }
        if (actorScope.tenantAdmin()) {
            if (!"TENANT_USER".equals(targetRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN can only create or update TENANT_USER");
            }
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported actor role");
    }

    private void enforceTargetUserAccess(AccessScope actorScope, AuthUser target) {
        if (actorScope.productAdmin()) {
            return;
        }
        if (!actorScope.tenantAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported actor role");
        }
        if (target.getTenantId() == null || !target.getTenantId().equals(actorScope.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN can only manage users from own tenant");
        }
        if (!"TENANT_USER".equalsIgnoreCase(target.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN can only manage TENANT_USER accounts");
        }
    }

    private Long resolveTenantIdFromCode(String tenantCode, String roleName) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id is required for " + roleName);
        }
        String normalizedTenantCode = tenantCode.trim().toLowerCase(Locale.ROOT);
        Optional<Tenant> tenant = tenantRepository.findActiveByTenantId(normalizedTenantCode);
        if (tenant.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id does not exist");
        }
        if (!"ACTIVE".equalsIgnoreCase(tenant.get().getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is not ACTIVE");
        }
        return tenant.get().getId();
    }

    private Long resolveOptionalTenantIdFromCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        return resolveTenantIdFromCode(tenantCode, "TENANT_USER");
    }

    private Long resolveTenantForList(AccessScope actorScope, String tenantCode, String roleFilter) {
        Long requestedTenantId = resolveOptionalTenantIdFromCode(tenantCode);
        if (actorScope.productAdmin()) {
            return requestedTenantId;
        }
        if (!actorScope.tenantAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported actor role");
        }

        Long actorTenantId = actorScope.tenantId();
        if (actorTenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN tenant scope is missing");
        }
        if (requestedTenantId != null && !actorTenantId.equals(requestedTenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN can only view own tenant users");
        }
        if (roleFilter != null && !"TENANT_USER".equals(roleFilter)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_ADMIN can only view TENANT_USER accounts");
        }
        return actorTenantId;
    }

    private int normalizePage(int page) {
        return Math.max(0, page);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private UserResponse toUserResponse(AuthUser user) {
        Long tenantId = user.getTenantId();
        String mappedTenantCode = resolveTenantCode(tenantId);
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getStatus(), mappedTenantCode);
    }

    private AccessScope resolveActorScope(UserPrincipal actorPrincipal) {
        if (actorPrincipal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String actor = actorPrincipal.username();
        String normalizedActor = (actor == null || actor.isBlank()) ? "ui" : actor.trim();
        String role = normalizeRole(actorPrincipal.role());
        return new AccessScope(
                normalizedActor,
                role,
                actorPrincipal.tenantId(),
                "PRODUCT_ADMIN".equals(role),
                "TENANT_ADMIN".equals(role)
        );
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

    private String resolveTenantCode(Long tenantMasterId) {
        if (tenantMasterId == null) {
            return null;
        }
        return tenantRepository.findById(tenantMasterId).map(Tenant::getTenantId).orElse(null);
    }

    private void recordAudit(String eventType,
                             String action,
                             String tenantId,
                             String actor,
                             Long userId,
                             Map<String, Object> metadata) {
        auditEventService.recordBestEffort(
                "USER_ADMIN",
                eventType,
                action,
                tenantId,
                actor,
                "AUTH_USER",
                userId == null ? null : String.valueOf(userId),
                "SUCCESS",
                metadata
        );
    }
}
