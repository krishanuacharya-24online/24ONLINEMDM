package com.e24online.mdm.service.enrollment;

import com.e24online.mdm.constants.DeviceEnrollmentServiceConstants;
import com.e24online.mdm.domain.DeviceEnrollment;
import com.e24online.mdm.domain.DeviceSetupKey;
import com.e24online.mdm.records.AgentEnrollmentClaim;
import com.e24online.mdm.records.SetupKeyIssue;
import com.e24online.mdm.records.devices.DeviceTokenPrincipal;
import com.e24online.mdm.records.devices.DeviceTokenRotation;
import com.e24online.mdm.repository.DeviceSetupKeyRepository;
import com.e24online.mdm.service.AuditEventService;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.TenantEntitlementService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceEnrollmentService {

    private final DeviceSetupKeyRepository setupKeyRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final BlockingDb blockingDb;
    private final AuditEventService auditEventService;
    private final TenantEntitlementService tenantEntitlementService;
    private final DeviceEnrollmentSupport support;
    private final DeviceSetupKeyService deviceSetupKeyService;
    private final EnrollmentManagementService enrollmentManagementService;
    private final DeviceCredentialService deviceCredentialService;
    private final TransactionTemplate transactionTemplate;

    public DeviceEnrollmentService(DeviceSetupKeyRepository setupKeyRepository,
                                   NamedParameterJdbcTemplate jdbc,
                                   BlockingDb blockingDb,
                                   AuditEventService auditEventService,
                                   TenantEntitlementService tenantEntitlementService,
                                   DeviceEnrollmentSupport support,
                                   DeviceSetupKeyService deviceSetupKeyService,
                                   EnrollmentManagementService enrollmentManagementService,
                                   DeviceCredentialService deviceCredentialService,
                                   TransactionTemplate transactionTemplate) {
        this.setupKeyRepository = setupKeyRepository;
        this.jdbc = jdbc;
        this.blockingDb = blockingDb;
        this.auditEventService = auditEventService;
        this.tenantEntitlementService = tenantEntitlementService;
        this.support = support;
        this.deviceSetupKeyService = deviceSetupKeyService;
        this.enrollmentManagementService = enrollmentManagementService;
        this.deviceCredentialService = deviceCredentialService;
        this.transactionTemplate = transactionTemplate;
    }

    public Mono<SetupKeyIssue> createSetupKeyAsync(String tenantId,
                                                   Long issuedByUserId,
                                                   Long targetUserId,
                                                   String actor,
                                                   Integer maxUses,
                                                   Integer ttlMinutes) {
        return blockingDb.mono(() -> {
            SetupKeyIssue issue = deviceSetupKeyService.createSetupKey(tenantId, issuedByUserId, targetUserId, actor, maxUses, ttlMinutes);
            String normalizedTenant = support.normalizeTenantId(tenantId);
            String effectiveActor = support.normalizeActor(actor);
            auditEventService.recordBestEffort(
                    DeviceEnrollmentServiceConstants.ENROLLMENT,
                    "SETUP_KEY_CREATED",
                    "CREATE",
                    normalizedTenant,
                    effectiveActor,
                    "DEVICE_SETUP_KEY",
                    issue.setupKeyId() == null ? null : String.valueOf(issue.setupKeyId()),
                    DeviceEnrollmentServiceConstants.SUCCESS,
                    Map.of(
                            "targetUserId", issue.targetUserId(),
                            "issuedByUserId", issue.issuedByUserId(),
                            "maxUses", issue.maxUses(),
                            "expiresAt", issue.expiresAt()
                    )
            );
            return issue;
        });
    }

    public Flux<DeviceEnrollment> listEnrollmentsAsync(String tenantId, String status, Long ownerUserId, int page, int size) {
        int safeSize = support.normalizePageSize(size);
        return blockingDb.flux(() -> {
            List<DeviceEnrollment> rows = enrollmentManagementService.listEnrollments(tenantId, status, ownerUserId, page, size);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("statusFilter", support.normalizeStatusFilter(status));
            metadata.put("ownerUserId", support.normalizeOptionalPositive(ownerUserId, DeviceEnrollmentServiceConstants.OWNER_USER_ID));
            metadata.put("page", Math.max(0, page));
            metadata.put("size", safeSize);
            metadata.put("resultCount", rows.size());
            auditEventService.recordBestEffort(
                    DeviceEnrollmentServiceConstants.ENROLLMENT,
                    DeviceEnrollmentServiceConstants.DEVICE_ENROLLMENTS_VIEWED,
                    "LIST",
                    support.normalizeTenantId(tenantId),
                    "ui",
                    DeviceEnrollmentServiceConstants.DEVICE_ENROLLMENT,
                    null,
                    DeviceEnrollmentServiceConstants.SUCCESS,
                    metadata
            );
            return rows;
        });
    }

    public Mono<DeviceEnrollment> getEnrollmentAsync(String tenantId, Long id, Long ownerUserId) {
        return blockingDb.mono(() -> {
            DeviceEnrollment enrollment = enrollmentManagementService.getEnrollment(tenantId, id, ownerUserId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(DeviceEnrollmentServiceConstants.ENROLLMENT_NO, enrollment.getEnrollmentNo());
            metadata.put("ownerUserIdFilter", support.normalizeOptionalPositive(ownerUserId, DeviceEnrollmentServiceConstants.OWNER_USER_ID));
            auditEventService.recordBestEffort(
                    DeviceEnrollmentServiceConstants.ENROLLMENT,
                    "DEVICE_ENROLLMENT_VIEWED",
                    "VIEW",
                    support.normalizeTenantId(tenantId),
                    "ui",
                    DeviceEnrollmentServiceConstants.DEVICE_ENROLLMENT,
                    enrollment.getId() == null ? null : String.valueOf(enrollment.getId()),
                    DeviceEnrollmentServiceConstants.SUCCESS,
                    metadata
            );
            return enrollment;
        });
    }

    public Mono<DeviceEnrollment> deEnrollAsync(String tenantId, String actor, Long ownerUserId, Long id, String reason) {
        return blockingDb.mono(() -> {
            DeviceEnrollment result = enrollmentManagementService.deEnroll(tenantId, actor, ownerUserId, id, reason);
            String normalizedTenant = support.normalizeTenantId(tenantId);
            String effectiveActor = support.normalizeActor(actor);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(DeviceEnrollmentServiceConstants.ENROLLMENT_NO, result.getEnrollmentNo());
            metadata.put("ownerUserId", result.getOwnerUserId());
            metadata.put("reason", result.getDeEnrollReason());
            auditEventService.recordBestEffort(
                    DeviceEnrollmentServiceConstants.ENROLLMENT,
                    "DEVICE_DE_ENROLLED",
                    "DE_ENROLL",
                    normalizedTenant,
                    effectiveActor,
                    DeviceEnrollmentServiceConstants.DEVICE_ENROLLMENT,
                    result.getId() == null ? null : String.valueOf(result.getId()),
                    DeviceEnrollmentServiceConstants.SUCCESS,
                    metadata
            );
            tenantEntitlementService.refreshUsageSnapshotForTenantCode(normalizedTenant);
            return result;
        });
    }

    public Mono<DeviceTokenRotation> rotateDeviceTokenAsync(String tenantId,
                                                            String actor,
                                                            Long ownerUserId,
                                                            Long enrollmentId) {
        return blockingDb.mono(() -> {
            DeviceTokenRotation rotation = deviceCredentialService.rotateDeviceToken(tenantId, actor, ownerUserId, enrollmentId);
            String normalizedTenant = support.normalizeTenantId(tenantId);
            String effectiveActor = support.normalizeActor(actor);
            auditEventService.recordBestEffort(
                    DeviceEnrollmentServiceConstants.ENROLLMENT,
                    "DEVICE_TOKEN_ROTATED",
                    "ROTATE",
                    normalizedTenant,
                    effectiveActor,
                    DeviceEnrollmentServiceConstants.DEVICE_ENROLLMENT,
                    rotation.enrollmentId() == null ? null : String.valueOf(rotation.enrollmentId()),
                    DeviceEnrollmentServiceConstants.SUCCESS,
                    Map.of(
                            DeviceEnrollmentServiceConstants.ENROLLMENT_NO, rotation.enrollmentNo(),
                            "tokenHint", rotation.tokenHint(),
                            "expiresAt", rotation.deviceTokenExpiresAt()
                    )
            );
            return rotation;
        });
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
        return blockingDb.mono(() -> claimWithSetupKey(qrToken, agentId, deviceFingerprint, deviceLabel));
    }

    public Mono<DeviceTokenPrincipal> authenticateDeviceTokenAsync(String rawToken) {
        return blockingDb.mono(() -> deviceCredentialService.authenticateDeviceToken(rawToken));
    }

    public Mono<Void> ensureActiveEnrollmentAsync(String tenantId, String enrollmentNo) {
        return blockingDb.run(() -> enrollmentManagementService.ensureActiveEnrollment(tenantId, enrollmentNo));
    }

    public void ensureActiveEnrollment(String tenantId, String enrollmentNo) {
        enrollmentManagementService.ensureActiveEnrollment(tenantId, enrollmentNo);
    }

    public AgentEnrollmentClaim claimWithSetupKey(String setupKey,
                                                   String agentId,
                                                   String deviceFingerprint,
                                                   String deviceLabel) {
        String normalizedSetupKey = support.normalizeSetupLikeToken(setupKey, "setup_key");
        String normalizedAgentId = support.normalizeRequired(agentId, "agent_id", 255);
        String normalizedFingerprint = support.normalizeOptional(deviceFingerprint, 255);
        String normalizedDeviceLabel = support.normalizeOptional(deviceLabel, 255);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String[] tenantRef = new String[1];
        Long[] setupKeyIdRef = new Long[1];
        String query = """
                    UPDATE device_setup_key
                    SET used_count = used_count + 1,
                        status = CASE WHEN used_count + 1 >= max_uses THEN 'CLAIMED' ELSE status END,
                        modified_at = :now,
                        modified_by = :actor
                    WHERE id = :id
                      AND status = 'ACTIVE'
                      AND expires_at > :now
                      AND used_count < max_uses
                    """;
        AgentEnrollmentClaim claim = requiredTransaction(() -> {
            String setupKeyHash = support.sha256Hex(normalizedSetupKey);
            List<DeviceSetupKey> matchingKeys = setupKeyRepository.findActiveByHash(setupKeyHash);
            if (matchingKeys.size() != 1) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid setup key");
            }
            DeviceSetupKey key = matchingKeys.getFirst();
            String normalizedTenant = support.normalizeTenantId(key.getTenantId());
            tenantRef[0] = normalizedTenant;
            setupKeyIdRef[0] = key.getId();
            Long targetUserId = support.requireActiveTenantUser(
                    support.requireActiveTenant(normalizedTenant, HttpStatus.UNAUTHORIZED).getId(),
                    key.getTargetUserId(),
                    HttpStatus.UNAUTHORIZED,
                    "target_user_id"
            ).getId();

            if (key.getExpiresAt() == null || !key.getExpiresAt().isAfter(now)) {
                key.setStatus(DeviceEnrollmentServiceConstants.EXPIRED);
                key.setModifiedAt(now);
                key.setModifiedBy("claim-expiry");
                setupKeyRepository.save(key);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid setup key");
            }

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("id", key.getId())
                    .addValue("now", now)
                    .addValue("actor", "setup-claim");
            int updated = jdbc.update(query, params);
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid setup key");
            }

            tenantEntitlementService.assertCanEnrollDevice(normalizedTenant);
            return enrollmentManagementService.createEnrollmentAndCredential(
                    normalizedTenant,
                    normalizedAgentId,
                    normalizedDeviceLabel,
                    normalizedFingerprint,
                    targetUserId,
                    key.getId()
            );
        });

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("setupKeyId", setupKeyIdRef[0]);
        metadata.put("agentId", normalizedAgentId);
        metadata.put("deviceFingerprint", normalizedFingerprint);
        metadata.put("deviceLabel", normalizedDeviceLabel);
        metadata.put(DeviceEnrollmentServiceConstants.ENROLLMENT_NO, claim.enrollmentNo());
        metadata.put("deviceTokenExpiresAt", claim.deviceTokenExpiresAt());
        auditEventService.recordBestEffort(
                DeviceEnrollmentServiceConstants.ENROLLMENT,
                "DEVICE_ENROLLMENT_CLAIMED",
                "CLAIM",
                tenantRef[0],
                normalizedAgentId,
                DeviceEnrollmentServiceConstants.DEVICE_ENROLLMENT,
                claim.enrollmentNo(),
                DeviceEnrollmentServiceConstants.SUCCESS,
                metadata
        );
        tenantEntitlementService.refreshUsageSnapshotForTenantCode(tenantRef[0]);
        return claim;
    }


    public <T> T requiredTransaction(java.util.function.Supplier<T> action) {
        T value = transactionTemplate.execute(_ -> action.get());
        return java.util.Objects.requireNonNull(value, "transaction returned null");
    }
}
