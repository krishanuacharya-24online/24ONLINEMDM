package com.e24online.mdm.service;

import com.e24online.mdm.constants.DeviceEnrollmentServiceConstants;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.DeviceAgentCredential;
import com.e24online.mdm.domain.DeviceEnrollment;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.AgentEnrollmentClaim;
import com.e24online.mdm.repository.DeviceAgentCredentialRepository;
import com.e24online.mdm.repository.DeviceEnrollmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
class EnrollmentManagementService {

    private final DeviceEnrollmentRepository enrollmentRepository;
    private final DeviceAgentCredentialRepository credentialRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final DeviceEnrollmentSupport support;
    private final DeviceCredentialService credentialService;

    EnrollmentManagementService(DeviceEnrollmentRepository enrollmentRepository,
                                DeviceAgentCredentialRepository credentialRepository,
                                NamedParameterJdbcTemplate jdbc,
                                TransactionTemplate transactionTemplate,
                                DeviceEnrollmentSupport support,
                                DeviceCredentialService credentialService) {
        this.enrollmentRepository = enrollmentRepository;
        this.credentialRepository = credentialRepository;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.support = support;
        this.credentialService = credentialService;
    }

    List<DeviceEnrollment> listEnrollments(String tenantId, String status, Long ownerUserId, int page, int size) {
        int safeSize = support.normalizePageSize(size);
        long offset = (long) Math.max(0, page) * safeSize;
        String normalizedStatus = support.normalizeStatusFilter(status);
        Long normalizedOwnerUserId = support.normalizeOptionalPositive(ownerUserId, DeviceEnrollmentServiceConstants.OWNER_USER_ID);
        String normalizedTenant = support.normalizeTenantId(tenantId);
        return enrollmentRepository.findPagedByTenant(normalizedTenant, normalizedStatus, normalizedOwnerUserId, safeSize, offset);
    }

    DeviceEnrollment getEnrollment(String tenantId, Long id, Long ownerUserId) {
        String normalizedTenant = support.normalizeTenantId(tenantId);
        Long normalizedOwnerUserId = support.normalizeOptionalPositive(ownerUserId, DeviceEnrollmentServiceConstants.OWNER_USER_ID);
        DeviceEnrollment enrollment = enrollmentRepository.findByIdAndTenant(id, normalizedTenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, DeviceEnrollmentServiceConstants.ENROLLMENT_NOT_FOUND));
        support.enforceOwnerScope(enrollment, normalizedOwnerUserId);
        return enrollment;
    }

    void ensureActiveEnrollment(String tenantId, String enrollmentNo) {
        String normalizedTenant = support.normalizeTenantId(tenantId);
        String normalizedEnrollmentNo = support.normalizeRequired(enrollmentNo, "device_external_id", 255);
        enrollmentRepository.findActiveByTenantAndEnrollmentNo(normalizedTenant, normalizedEnrollmentNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "device_external_id is not enrolled or is de-enrolled"));
    }

    DeviceEnrollment deEnroll(String tenantId, String actor, Long ownerUserId, Long id, String reason) {
        String normalizedTenant = support.normalizeTenantId(tenantId);
        Long normalizedOwnerUserId = support.normalizeOptionalPositive(ownerUserId, DeviceEnrollmentServiceConstants.OWNER_USER_ID);
        String effectiveActor = support.normalizeActor(actor);
        String normalizedReason = support.normalizeOptional(reason, 1000);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String query = """
                    UPDATE device_trust_profile
                    SET is_deleted = true,
                        modified_at = :now,
                        modified_by = :actor
                    WHERE is_deleted = false
                      AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
                      AND device_external_id = :deviceExternalId
                    """;
        return requiredTransaction(() -> {
            DeviceEnrollment enrollment = enrollmentRepository.findByIdAndTenant(id, normalizedTenant)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, DeviceEnrollmentServiceConstants.ENROLLMENT_NOT_FOUND));
            support.enforceOwnerScope(enrollment, normalizedOwnerUserId);
            if (DeviceEnrollmentServiceConstants.DE_ENROLLED.equals(enrollment.getStatus())) {
                return enrollment;
            }

            enrollment.setStatus(DeviceEnrollmentServiceConstants.DE_ENROLLED);
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
            jdbc.update(query, profileParams);
            return saved;
        });
    }

    AgentEnrollmentClaim createEnrollmentAndCredential(String tenantId,
                                                       String agentId,
                                                       String deviceLabel,
                                                       String deviceFingerprint,
                                                       Long ownerUserId,
                                                       Long setupKeyId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Tenant tenant = support.requireActiveTenant(tenantId, HttpStatus.UNAUTHORIZED);
        AuthUser owner = support.requireActiveTenantUser(tenant.getId(), ownerUserId, HttpStatus.UNAUTHORIZED, DeviceEnrollmentServiceConstants.OWNER_USER_ID);
        String enrollmentNo = support.generateEnrollmentNo(tenantId);
        DeviceEnrollment enrollment = new DeviceEnrollment();
        enrollment.setTenantId(tenantId);
        enrollment.setEnrollmentNo(enrollmentNo);
        enrollment.setEnrollmentMethod("SETUP_KEY");
        enrollment.setStatus(DeviceEnrollmentServiceConstants.ACTIVE);
        enrollment.setAgentId(agentId);
        enrollment.setDeviceLabel(deviceLabel);
        enrollment.setDeviceFingerprint(deviceFingerprint);
        enrollment.setOwnerUserId(owner.getId());
        enrollment.setSetupKeyId(setupKeyId);
        enrollment.setEnrolledAt(now);
        enrollment.setCreatedAt(now);
        enrollment.setCreatedBy(DeviceEnrollmentServiceConstants.AGENT_ENROLL);
        enrollment.setModifiedAt(now);
        enrollment.setModifiedBy(DeviceEnrollmentServiceConstants.AGENT_ENROLL);
        DeviceEnrollment savedEnrollment = enrollmentRepository.save(enrollment);

        DeviceCredentialService.AgentCredentialIssue credentialIssue =
                credentialService.createCredential(tenantId, savedEnrollment.getId(), "agent-enroll");

        return new AgentEnrollmentClaim(
                savedEnrollment.getEnrollmentNo(),
                credentialIssue.rawToken(),
                credentialIssue.savedCredential().getTokenHint(),
                credentialIssue.savedCredential().getExpiresAt()
        );
    }

    private <T> T requiredTransaction(Supplier<T> action) {
        T value = transactionTemplate.execute(_ -> action.get());
        return Objects.requireNonNull(value, "transaction returned null");
    }
}
