package com.e24online.mdm.service;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class DeviceEnrollmentService {

    private static final int DEFAULT_SETUP_KEY_MAX_USES = 5;
    private static final int DEFAULT_SETUP_KEY_TTL_MINUTES = 60;
    private static final int SETUP_CODE_RAW_LENGTH = 12;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int ENROLLMENT_NO_SUFFIX_LENGTH = 10;
    private static final String ALL_CHAR_NUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Pattern COMPACT_SETUP_CODE = Pattern.compile("^[A-Za-z0-9]{12}$");
    private static final Pattern GROUPED_SETUP_CODE = Pattern.compile("^[A-Za-z0-9]{3}(?:-[A-Za-z0-9]{3}){3}$");

    private final DeviceEnrollmentRepository enrollmentRepository;
    private final DeviceSetupKeyRepository setupKeyRepository;
    private final DeviceAgentCredentialRepository credentialRepository;
    private final AuthUserRepository authUserRepository;
    private final TenantRepository tenantRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final BlockingDb blockingDb;
    private final TransactionTemplate transactionTemplate;
    private final AuditEventService auditEventService;
    private final long deviceTokenTtlMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceEnrollmentService(DeviceEnrollmentRepository enrollmentRepository,
                                   DeviceSetupKeyRepository setupKeyRepository,
                                   DeviceAgentCredentialRepository credentialRepository,
                                   AuthUserRepository authUserRepository,
                                   TenantRepository tenantRepository,
                                   NamedParameterJdbcTemplate jdbc,
                                   BlockingDb blockingDb,
                                   TransactionTemplate transactionTemplate,
                                   AuditEventService auditEventService,
                                   @Value("${security.device-token.ttl-minutes:10080}") long deviceTokenTtlMinutes) {
        this.enrollmentRepository = enrollmentRepository;
        this.setupKeyRepository = setupKeyRepository;
        this.credentialRepository = credentialRepository;
        this.authUserRepository = authUserRepository;
        this.tenantRepository = tenantRepository;
        this.jdbc = jdbc;
        this.blockingDb = blockingDb;
        this.transactionTemplate = transactionTemplate;
        this.auditEventService = auditEventService;
        this.deviceTokenTtlMinutes = Math.max(1L, deviceTokenTtlMinutes);
    }

    public Mono<SetupKeyIssue> createSetupKeyAsync(String tenantId,
                                                   Long issuedByUserId,
                                                   Long targetUserId,
                                                   String actor,
                                                   Integer maxUses,
                                                   Integer ttlMinutes) {
        return blockingDb.mono(() -> createSetupKey(tenantId, issuedByUserId, targetUserId, actor, maxUses, ttlMinutes));
    }

    public Flux<DeviceEnrollment> listEnrollmentsAsync(String tenantId, String status, Long ownerUserId, int page, int size) {
        int safeSize = normalizePageSize(size);
        long offset = (long) Math.max(0, page) * safeSize;
        String normalizedStatus = normalizeStatusFilter(status);
        Long normalizedOwnerUserId = normalizeOptionalPositive(ownerUserId, "owner_user_id");
        String normalizedTenant = normalizeTenantId(tenantId);
        return blockingDb.flux(() -> {
            java.util.List<DeviceEnrollment> rows = enrollmentRepository.findPagedByTenant(
                    normalizedTenant,
                    normalizedStatus,
                    normalizedOwnerUserId,
                    safeSize,
                    offset
            );
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("statusFilter", normalizedStatus);
            metadata.put("ownerUserId", normalizedOwnerUserId);
            metadata.put("page", Math.max(0, page));
            metadata.put("size", safeSize);
            metadata.put("resultCount", rows.size());
            auditEventService.recordBestEffort(
                    "ENROLLMENT",
                    "DEVICE_ENROLLMENTS_VIEWED",
                    "LIST",
                    normalizedTenant,
                    "ui",
                    "DEVICE_ENROLLMENT",
                    null,
                    "SUCCESS",
                    metadata
            );
            return rows;
        });
    }

    public Mono<DeviceEnrollment> getEnrollmentAsync(String tenantId, Long id, Long ownerUserId) {
        return blockingDb.mono(() -> {
            DeviceEnrollment enrollment = getEnrollment(tenantId, id, ownerUserId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("enrollmentNo", enrollment.getEnrollmentNo());
            metadata.put("ownerUserIdFilter", normalizeOptionalPositive(ownerUserId, "owner_user_id"));
            auditEventService.recordBestEffort(
                    "ENROLLMENT",
                    "DEVICE_ENROLLMENT_VIEWED",
                    "VIEW",
                    normalizeTenantId(tenantId),
                    "ui",
                    "DEVICE_ENROLLMENT",
                    enrollment.getId() == null ? null : String.valueOf(enrollment.getId()),
                    "SUCCESS",
                    metadata
            );
            return enrollment;
        });
    }

    public Mono<DeviceEnrollment> deEnrollAsync(String tenantId, String actor, Long ownerUserId, Long id, String reason) {
        return blockingDb.mono(() -> deEnroll(tenantId, actor, ownerUserId, id, reason));
    }

    public Mono<DeviceTokenRotation> rotateDeviceTokenAsync(String tenantId,
                                                            String actor,
                                                            Long ownerUserId,
                                                            Long enrollmentId) {
        return blockingDb.mono(() -> rotateDeviceToken(tenantId, actor, ownerUserId, enrollmentId));
    }

    public Mono<AgentEnrollmentClaim> claimWithSetupKeyAsync(String setupKey,
                                                             String agentId,
                                                             String deviceFingerprint,
                                                             String deviceLabel) {
        return blockingDb.mono(() -> claimWithSetupKey(setupKey, agentId, deviceFingerprint, deviceLabel));
    }

    public Mono<AgentEnrollmentClaim> claimWithQrAsync(String qrToken,
                                                       String agentId,
                                                       String deviceFingerprint,
                                                       String deviceLabel) {
        // QR now carries the setup key string; both claim paths are identical.
        return blockingDb.mono(() -> claimWithSetupKey(qrToken, agentId, deviceFingerprint, deviceLabel));
    }

    public Mono<DeviceTokenPrincipal> authenticateDeviceTokenAsync(String rawToken) {
        return blockingDb.mono(() -> authenticateDeviceToken(rawToken));
    }

    public Mono<Void> ensureActiveEnrollmentAsync(String tenantId, String enrollmentNo) {
        return blockingDb.run(() -> ensureActiveEnrollment(tenantId, enrollmentNo));
    }

    public void ensureActiveEnrollment(String tenantId, String enrollmentNo) {
        String normalizedTenant = normalizeTenantId(tenantId);
        String normalizedEnrollmentNo = normalizeRequired(enrollmentNo, "device_external_id", 255);
        enrollmentRepository.findActiveByTenantAndEnrollmentNo(normalizedTenant, normalizedEnrollmentNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "device_external_id is not enrolled or is de-enrolled"
                ));
    }

    private SetupKeyIssue createSetupKey(String tenantId,
                                         Long issuedByUserId,
                                         Long targetUserId,
                                         String actor,
                                         Integer maxUses,
                                         Integer ttlMinutes) {
        Tenant activeTenant = requireActiveTenant(normalizeTenantId(tenantId), HttpStatus.FORBIDDEN);
        String normalizedTenant = activeTenant.getTenantId();
        AuthUser issuer = requireActiveTenantUser(activeTenant.getId(), issuedByUserId, HttpStatus.FORBIDDEN, "issuer_user_id");
        AuthUser targetUser = requireActiveTenantUser(activeTenant.getId(), targetUserId, HttpStatus.BAD_REQUEST, "target_user_id");
        String effectiveActor = normalizeActor(actor);
        int safeMaxUses = normalizeBounded(maxUses, DEFAULT_SETUP_KEY_MAX_USES, 1, 1000, "max_uses");
        int safeTtlMinutes = normalizeBounded(ttlMinutes, DEFAULT_SETUP_KEY_TTL_MINUTES, 1, 7 * 24 * 60, "ttl_minutes");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusMinutes(safeTtlMinutes);
        String rawKey = generateSetupCode();

        DeviceSetupKey entity = new DeviceSetupKey();
        entity.setTenantId(normalizedTenant);
        entity.setKeyHash(sha256Hex(rawKey));
        entity.setKeyHint(mask(rawKey));
        entity.setStatus("ACTIVE");
        entity.setMaxUses(safeMaxUses);
        entity.setUsedCount(0);
        entity.setExpiresAt(expiresAt);
        entity.setIssuedByUserId(issuer.getId());
        entity.setTargetUserId(targetUser.getId());
        entity.setCreatedAt(now);
        entity.setCreatedBy(effectiveActor);
        entity.setModifiedAt(now);
        entity.setModifiedBy(effectiveActor);
        DeviceSetupKey saved = setupKeyRepository.save(entity);
        SetupKeyIssue issue = new SetupKeyIssue(
                saved.getId(),
                rawKey,
                saved.getKeyHint(),
                saved.getExpiresAt(),
                saved.getMaxUses(),
                saved.getTargetUserId(),
                saved.getIssuedByUserId()
        );
        auditEventService.recordBestEffort(
                "ENROLLMENT",
                "SETUP_KEY_CREATED",
                "CREATE",
                normalizedTenant,
                effectiveActor,
                "DEVICE_SETUP_KEY",
                saved.getId() == null ? null : String.valueOf(saved.getId()),
                "SUCCESS",
                Map.of(
                        "targetUserId", saved.getTargetUserId(),
                        "issuedByUserId", saved.getIssuedByUserId(),
                        "maxUses", saved.getMaxUses(),
                        "ttlMinutes", safeTtlMinutes,
                        "expiresAt", saved.getExpiresAt()
                )
        );
        return issue;
    }

    private DeviceEnrollment getEnrollment(String tenantId, Long id, Long ownerUserId) {
        String normalizedTenant = normalizeTenantId(tenantId);
        Long normalizedOwnerUserId = normalizeOptionalPositive(ownerUserId, "owner_user_id");
        DeviceEnrollment enrollment = enrollmentRepository.findByIdAndTenant(id, normalizedTenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
        enforceOwnerScope(enrollment, normalizedOwnerUserId);
        return enrollment;
    }

    private DeviceEnrollment deEnroll(String tenantId, String actor, Long ownerUserId, Long id, String reason) {
        String normalizedTenant = normalizeTenantId(tenantId);
        Long normalizedOwnerUserId = normalizeOptionalPositive(ownerUserId, "owner_user_id");
        String effectiveActor = normalizeActor(actor);
        String normalizedReason = normalizeOptional(reason, 1000);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        DeviceEnrollment result = requiredTransaction(() -> {
            DeviceEnrollment enrollment = enrollmentRepository.findByIdAndTenant(id, normalizedTenant)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
            enforceOwnerScope(enrollment, normalizedOwnerUserId);
            if ("DE_ENROLLED".equals(enrollment.getStatus())) {
                return enrollment;
            }

            enrollment.setStatus("DE_ENROLLED");
            enrollment.setDeEnrolledAt(now);
            enrollment.setDeEnrollReason(normalizedReason);
            enrollment.setModifiedAt(now);
            enrollment.setModifiedBy(effectiveActor);
            DeviceEnrollment saved = enrollmentRepository.save(enrollment);

            List<DeviceAgentCredential> credentials = credentialRepository.findActiveByEnrollmentId(saved.getId());
            for (DeviceAgentCredential credential : credentials) {
                credential.setStatus("REVOKED");
                credential.setRevokedAt(now);
                credential.setRevokedBy(effectiveActor);
                credentialRepository.save(credential);
            }

            MapSqlParameterSource profileParams = new MapSqlParameterSource()
                    .addValue("tenantId", normalizedTenant)
                    .addValue("deviceExternalId", saved.getEnrollmentNo())
                    .addValue("now", now)
                    .addValue("actor", effectiveActor);
            jdbc.update("""
                    UPDATE device_trust_profile
                    SET is_deleted = true,
                        modified_at = :now,
                        modified_by = :actor
                    WHERE is_deleted = false
                      AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
                      AND device_external_id = :deviceExternalId
                    """, profileParams);
            return saved;
        });
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("enrollmentNo", result.getEnrollmentNo());
        metadata.put("ownerUserId", result.getOwnerUserId());
        metadata.put("reason", result.getDeEnrollReason());

        auditEventService.recordBestEffort(
                "ENROLLMENT",
                "DEVICE_DE_ENROLLED",
                "DE_ENROLL",
                normalizedTenant,
                effectiveActor,
                "DEVICE_ENROLLMENT",
                result.getId() == null ? null : String.valueOf(result.getId()),
                "SUCCESS",
                metadata
        );
        return result;
    }

    private AgentEnrollmentClaim claimWithSetupKey(String setupKey,
                                                   String agentId,
                                                   String deviceFingerprint,
                                                   String deviceLabel) {
        String normalizedSetupKey = normalizeSetupLikeToken(setupKey, "setup_key");
        String normalizedAgentId = normalizeRequired(agentId, "agent_id", 255);
        String normalizedFingerprint = normalizeOptional(deviceFingerprint, 255);
        String normalizedDeviceLabel = normalizeOptional(deviceLabel, 255);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String[] tenantRef = new String[1];
        Long[] setupKeyIdRef = new Long[1];

        AgentEnrollmentClaim claim = requiredTransaction(() -> {
            String setupKeyHash = sha256Hex(normalizedSetupKey);
            List<DeviceSetupKey> matchingKeys = setupKeyRepository.findActiveByHash(setupKeyHash);
            if (matchingKeys.size() != 1) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid setup key");
            }
            DeviceSetupKey key = matchingKeys.getFirst();
            String normalizedTenant = normalizeTenantId(key.getTenantId());
            tenantRef[0] = normalizedTenant;
            setupKeyIdRef[0] = key.getId();
            Tenant activeTenant = requireActiveTenant(normalizedTenant, HttpStatus.UNAUTHORIZED);
            Long targetUserId = requireActiveTenantUser(
                    activeTenant.getId(),
                    key.getTargetUserId(),
                    HttpStatus.UNAUTHORIZED,
                    "target_user_id"
            ).getId();

            if (key.getExpiresAt() == null || !key.getExpiresAt().isAfter(now)) {
                key.setStatus("EXPIRED");
                key.setModifiedAt(now);
                key.setModifiedBy("claim-expiry");
                setupKeyRepository.save(key);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid setup key");
            }

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("id", key.getId())
                    .addValue("now", now)
                    .addValue("actor", "setup-claim");
            int updated = jdbc.update("""
                    UPDATE device_setup_key
                    SET used_count = used_count + 1,
                        status = CASE WHEN used_count + 1 >= max_uses THEN 'CLAIMED' ELSE status END,
                        modified_at = :now,
                        modified_by = :actor
                    WHERE id = :id
                      AND status = 'ACTIVE'
                      AND expires_at > :now
                      AND used_count < max_uses
                    """, params);
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid setup key");
            }

            return createEnrollmentAndCredential(
                    normalizedTenant,
                    normalizedAgentId,
                    normalizedDeviceLabel,
                    normalizedFingerprint,
                    targetUserId,
                    key.getId()
            );
        });
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("setupKeyId", setupKeyIdRef[0]);
        metadata.put("agentId", normalizedAgentId);
        metadata.put("deviceFingerprint", normalizedFingerprint);
        metadata.put("deviceLabel", normalizedDeviceLabel);
        metadata.put("enrollmentNo", claim.enrollmentNo());
        metadata.put("deviceTokenExpiresAt", claim.deviceTokenExpiresAt());

        auditEventService.recordBestEffort(
                "ENROLLMENT",
                "DEVICE_ENROLLMENT_CLAIMED",
                "CLAIM",
                tenantRef[0],
                normalizedAgentId,
                "DEVICE_ENROLLMENT",
                claim.enrollmentNo(),
                "SUCCESS",
                metadata
        );
        return claim;
    }

    private DeviceTokenPrincipal authenticateDeviceToken(String rawToken) {
        String normalizedToken = normalizeRequired(rawToken, "X-Device-Token", 1024);
        String tokenHash = sha256Hex(normalizedToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DeviceAgentCredential credential = credentialRepository.findActiveByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token"));

        if (credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(now)) {
            credential.setStatus("EXPIRED");
            credential.setRevokedAt(now);
            credential.setRevokedBy("device-token-expiry");
            credentialRepository.save(credential);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token");
        }

        DeviceEnrollment enrollment = enrollmentRepository.findById(credential.getDeviceEnrollmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token"));
        if (!"ACTIVE".equals(enrollment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Enrollment is not active");
        }

        String enrollmentTenant = normalizeTenantId(enrollment.getTenantId());
        if (!enrollmentTenant.equals(normalizeTenantId(credential.getTenantId()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token");
        }

        return new DeviceTokenPrincipal(enrollmentTenant, enrollment.getEnrollmentNo(), enrollment.getId());
    }

    private AgentEnrollmentClaim createEnrollmentAndCredential(String tenantId,
                                                               String agentId,
                                                               String deviceLabel,
                                                               String deviceFingerprint,
                                                               Long ownerUserId,
                                                               Long setupKeyId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Tenant tenant = requireActiveTenant(tenantId, HttpStatus.UNAUTHORIZED);
        AuthUser owner = requireActiveTenantUser(tenant.getId(), ownerUserId, HttpStatus.UNAUTHORIZED, "owner_user_id");
        String enrollmentNo = generateEnrollmentNo(tenantId);
        DeviceEnrollment enrollment = new DeviceEnrollment();
        enrollment.setTenantId(tenantId);
        enrollment.setEnrollmentNo(enrollmentNo);
        enrollment.setEnrollmentMethod("SETUP_KEY");
        enrollment.setStatus("ACTIVE");
        enrollment.setAgentId(agentId);
        enrollment.setDeviceLabel(deviceLabel);
        enrollment.setDeviceFingerprint(deviceFingerprint);
        enrollment.setOwnerUserId(owner.getId());
        enrollment.setSetupKeyId(setupKeyId);
        enrollment.setEnrolledAt(now);
        enrollment.setCreatedAt(now);
        enrollment.setCreatedBy("agent-enroll");
        enrollment.setModifiedAt(now);
        enrollment.setModifiedBy("agent-enroll");
        DeviceEnrollment savedEnrollment = enrollmentRepository.save(enrollment);

        String rawToken = generateSecret("dvt");
        DeviceAgentCredential credential = new DeviceAgentCredential();
        credential.setTenantId(tenantId);
        credential.setDeviceEnrollmentId(savedEnrollment.getId());
        credential.setTokenHash(sha256Hex(rawToken));
        credential.setTokenHint(mask(rawToken));
        credential.setStatus("ACTIVE");
        credential.setExpiresAt(now.plusMinutes(deviceTokenTtlMinutes));
        credential.setCreatedAt(now);
        credential.setCreatedBy("agent-enroll");
        DeviceAgentCredential savedCredential = credentialRepository.save(credential);

        return new AgentEnrollmentClaim(
                savedEnrollment.getEnrollmentNo(),
                rawToken,
                savedCredential.getTokenHint(),
                savedCredential.getExpiresAt()
        );
    }

    private DeviceTokenRotation rotateDeviceToken(String tenantId,
                                                  String actor,
                                                  Long ownerUserId,
                                                  Long enrollmentId) {
        String normalizedTenant = normalizeTenantId(tenantId);
        Long normalizedOwnerUserId = normalizeOptionalPositive(ownerUserId, "owner_user_id");
        String effectiveActor = normalizeActor(actor);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        DeviceTokenRotation rotation = requiredTransaction(() -> {
            DeviceEnrollment enrollment = enrollmentRepository.findByIdAndTenant(enrollmentId, normalizedTenant)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
            enforceOwnerScope(enrollment, normalizedOwnerUserId);
            if (!"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Enrollment is not ACTIVE");
            }

            for (DeviceAgentCredential existing : credentialRepository.findActiveByEnrollmentId(enrollment.getId())) {
                existing.setStatus("REVOKED");
                existing.setRevokedAt(now);
                existing.setRevokedBy(effectiveActor);
                credentialRepository.save(existing);
            }

            String rawToken = generateSecret("dvt");
            DeviceAgentCredential rotated = new DeviceAgentCredential();
            rotated.setTenantId(normalizedTenant);
            rotated.setDeviceEnrollmentId(enrollment.getId());
            rotated.setTokenHash(sha256Hex(rawToken));
            rotated.setTokenHint(mask(rawToken));
            rotated.setStatus("ACTIVE");
            rotated.setExpiresAt(now.plusMinutes(deviceTokenTtlMinutes));
            rotated.setCreatedAt(now);
            rotated.setCreatedBy(effectiveActor);
            DeviceAgentCredential saved = credentialRepository.save(rotated);

            return new DeviceTokenRotation(
                    enrollment.getId(),
                    enrollment.getEnrollmentNo(),
                    rawToken,
                    saved.getTokenHint(),
                    saved.getExpiresAt()
            );
        });
        auditEventService.recordBestEffort(
                "ENROLLMENT",
                "DEVICE_TOKEN_ROTATED",
                "ROTATE",
                normalizedTenant,
                effectiveActor,
                "DEVICE_ENROLLMENT",
                rotation.enrollmentId() == null ? null : String.valueOf(rotation.enrollmentId()),
                "SUCCESS",
                Map.of(
                        "enrollmentNo", rotation.enrollmentNo(),
                        "tokenHint", rotation.tokenHint(),
                        "expiresAt", rotation.deviceTokenExpiresAt()
                )
        );
        return rotation;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeStatusFilter(String status) {
        String normalized = normalizeOptional(status, 64);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(upper) && !"DE_ENROLLED".equals(upper) && !"EXPIRED".equals(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        return upper;
    }

    private int normalizeBounded(Integer value, int defaultValue, int min, int max, String field) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < min || resolved > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is out of range");
        }
        return resolved;
    }

    private Long normalizeOptionalPositive(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be positive");
        }
        return value;
    }

    private Long normalizeRequiredPositive(Long value, String fieldName) {
        Long normalized = normalizeOptionalPositive(value, fieldName);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeTenantId(String tenantId) {
        String normalized = normalizeRequired(tenantId, "tenant_id", 64).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id is required");
        }
        return normalized;
    }

    private String normalizeActor(String actor) {
        String normalized = normalizeOptional(actor, 128);
        return normalized == null ? "system" : normalized;
    }

    private String normalizeSetupLikeToken(String value, String fieldName) {
        String normalized = normalizeRequired(value, fieldName, 512);
        String compact = normalized.replace("-", "").replace(" ", "");
        if (COMPACT_SETUP_CODE.matcher(compact).matches()
                || GROUPED_SETUP_CODE.matcher(normalized).matches()) {
            String upper = compact.toUpperCase(Locale.ROOT);
            return upper.substring(0, 3) + "-"
                    + upper.substring(3, 6) + "-"
                    + upper.substring(6, 9) + "-"
                    + upper.substring(9, 12);
        }
        return normalized;
    }

    private String normalizeRequired(String value, String fieldName, int maxLen) {
        String normalized = normalizeOptional(value, maxLen);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLen) {
            return normalized.substring(0, maxLen);
        }
        return normalized;
    }

    private Tenant requireActiveTenant(String tenantId, HttpStatus status) {
        return tenantRepository.findActiveByTenantId(tenantId)
                .filter(t -> !t.isDeleted() && "ACTIVE".equalsIgnoreCase(t.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(status, "Invalid tenant"));
    }

    private AuthUser requireActiveTenantUser(Long tenantMasterId, Long userId, HttpStatus status, String fieldName) {
        Long normalizedUserId = normalizeRequiredPositive(userId, fieldName);
        Long normalizedTenantMasterId = normalizeRequiredPositive(tenantMasterId, "tenant_master_id");
        AuthUser user = authUserRepository.findActiveByIdAndTenantId(normalizedUserId, normalizedTenantMasterId)
                .orElseThrow(() -> new ResponseStatusException(status, "Invalid " + fieldName));
        String role = normalizeOptional(user.getRole(), 64);
        if (role == null || "PRODUCT_ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(status, "Invalid " + fieldName);
        }
        return user;
    }

    private void enforceOwnerScope(DeviceEnrollment enrollment, Long requiredOwnerUserId) {
        if (requiredOwnerUserId == null) {
            return;
        }
        if (requiredOwnerUserId.equals(enrollment.getOwnerUserId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found");
    }

    private String generateSecret(String prefix) {
        byte[] randomBytes = new byte[36];
        secureRandom.nextBytes(randomBytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return prefix + "_" + token;
    }

    private String generateSetupCode() {
        String raw = randomAlnum(SETUP_CODE_RAW_LENGTH);
        return raw.substring(0, 3) + "-"
                + raw.substring(3, 6) + "-"
                + raw.substring(6, 9) + "-"
                + raw.substring(9, 12);
    }

    private String generateEnrollmentNo(String tenantId) {
        String cleanedTenant = tenantId.replaceAll("[^a-z0-9]", "").toUpperCase(Locale.ROOT);
        if (cleanedTenant.isBlank()) {
            cleanedTenant = "TENANT";
        }
        if (cleanedTenant.length() > 8) {
            cleanedTenant = cleanedTenant.substring(0, 8);
        }

        for (int i = 0; i < 12; i++) {
            String suffix = randomAlnum(ENROLLMENT_NO_SUFFIX_LENGTH);
            String candidate = "ENR-" + cleanedTenant + "-" + suffix;
            Optional<DeviceEnrollment> existing = enrollmentRepository.findByTenantAndEnrollmentNo(tenantId, candidate);
            if (existing.isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate enrollment number");
    }

    private String randomAlnum(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int idx = secureRandom.nextInt(ALL_CHAR_NUM.length());
            sb.append(ALL_CHAR_NUM.charAt(idx));
        }
        return sb.toString();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash value", ex);
        }
    }

    private String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "hidden";
        }
        if (raw.length() <= 14) {
            return raw;
        }
        return raw.substring(0, 6) + "..." + raw.substring(raw.length() - 6);
    }

    private <T> T requiredTransaction(Supplier<T> action) {
        T value = transactionTemplate.execute(status -> action.get());
        return Objects.requireNonNull(value, "transaction returned null");
    }

    public record SetupKeyIssue(
            Long setupKeyId,
            String setupKey,
            String keyHint,
            OffsetDateTime expiresAt,
            Integer maxUses,
            Long targetUserId,
            Long issuedByUserId
    ) {
    }

    public record AgentEnrollmentClaim(
            String enrollmentNo,
            String deviceToken,
            String tokenHint,
            OffsetDateTime deviceTokenExpiresAt
    ) {
    }

    public record DeviceTokenPrincipal(
            String tenantId,
            String enrollmentNo,
            Long enrollmentId
    ) {
    }

    public record DeviceTokenRotation(
            Long enrollmentId,
            String enrollmentNo,
            String deviceToken,
            String tokenHint,
            OffsetDateTime deviceTokenExpiresAt
    ) {
    }
}
