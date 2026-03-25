package com.e24online.mdm.service.enrollment;

import com.e24online.mdm.constants.DeviceEnrollmentServiceConstants;
import com.e24online.mdm.domain.DeviceAgentCredential;
import com.e24online.mdm.domain.DeviceEnrollment;
import com.e24online.mdm.records.devices.DeviceTokenPrincipal;
import com.e24online.mdm.records.devices.DeviceTokenRotation;
import com.e24online.mdm.repository.DeviceAgentCredentialRepository;
import com.e24online.mdm.repository.DeviceEnrollmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class DeviceCredentialService {

    private final DeviceAgentCredentialRepository credentialRepository;
    private final DeviceEnrollmentRepository enrollmentRepository;
    private final DeviceEnrollmentSupport support;
    private final TransactionTemplate transactionTemplate;
    private final long deviceTokenTtlMinutes;

    public DeviceCredentialService(DeviceAgentCredentialRepository credentialRepository,
                            DeviceEnrollmentRepository enrollmentRepository,
                            DeviceEnrollmentSupport support,
                            TransactionTemplate transactionTemplate,
                            @Value("${security.device-token.ttl-minutes:10080}") long deviceTokenTtlMinutes) {
        this.credentialRepository = credentialRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.support = support;
        this.transactionTemplate = transactionTemplate;
        this.deviceTokenTtlMinutes = Math.max(1L, deviceTokenTtlMinutes);
    }

    public DeviceTokenPrincipal authenticateDeviceToken(String rawToken) {
        String normalizedToken = support.normalizeRequired(rawToken, "X-Device-Token", 1024);
        String tokenHash = support.sha256Hex(normalizedToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DeviceAgentCredential credential = credentialRepository.findActiveByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token"));

        if (credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(now)) {
            credential.setStatus(DeviceEnrollmentServiceConstants.EXPIRED);
            credential.setRevokedAt(now);
            credential.setRevokedBy("device-token-expiry");
            credentialRepository.save(credential);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token");
        }

        DeviceEnrollment enrollment = enrollmentRepository.findById(credential.getDeviceEnrollmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token"));
        if (!DeviceEnrollmentServiceConstants.ACTIVE.equals(enrollment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Enrollment is not active");
        }

        String enrollmentTenant = support.normalizeTenantId(enrollment.getTenantId());
        if (!enrollmentTenant.equals(support.normalizeTenantId(credential.getTenantId()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Device-Token");
        }

        return new DeviceTokenPrincipal(enrollmentTenant, enrollment.getEnrollmentNo(), enrollment.getId());
    }

    public DeviceTokenRotation rotateDeviceToken(String tenantId,
                                                 String actor,
                                                 Long ownerUserId,
                                                 Long enrollmentId) {
        String normalizedTenant = support.normalizeTenantId(tenantId);
        Long normalizedOwnerUserId = support.normalizeOptionalPositive(ownerUserId, DeviceEnrollmentServiceConstants.OWNER_USER_ID);
        String effectiveActor = support.normalizeActor(actor);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return requiredTransaction(() -> {
            DeviceEnrollment enrollment = enrollmentRepository.findByIdAndTenant(enrollmentId, normalizedTenant)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, DeviceEnrollmentServiceConstants.ENROLLMENT_NOT_FOUND));
            support.enforceOwnerScope(enrollment, normalizedOwnerUserId);
            if (!DeviceEnrollmentServiceConstants.ACTIVE.equalsIgnoreCase(enrollment.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Enrollment is not ACTIVE");
            }

            for (DeviceAgentCredential existing : credentialRepository.findActiveByEnrollmentId(enrollment.getId())) {
                existing.setStatus("REVOKED");
                existing.setRevokedAt(now);
                existing.setRevokedBy(effectiveActor);
                credentialRepository.save(existing);
            }

            String rawToken = support.generateSecret("dvt");
            DeviceAgentCredential rotated = new DeviceAgentCredential();
            rotated.setTenantId(normalizedTenant);
            rotated.setDeviceEnrollmentId(enrollment.getId());
            rotated.setTokenHash(support.sha256Hex(rawToken));
            rotated.setTokenHint(support.mask(rawToken));
            rotated.setStatus(DeviceEnrollmentServiceConstants.ACTIVE);
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
    }

    public AgentCredentialIssue createCredential(String tenantId, Long enrollmentId, String createdBy) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String rawToken = support.generateSecret("dvt");
        DeviceAgentCredential credential = new DeviceAgentCredential();
        credential.setTenantId(tenantId);
        credential.setDeviceEnrollmentId(enrollmentId);
        credential.setTokenHash(support.sha256Hex(rawToken));
        credential.setTokenHint(support.mask(rawToken));
        credential.setStatus(DeviceEnrollmentServiceConstants.ACTIVE);
        credential.setExpiresAt(now.plusMinutes(deviceTokenTtlMinutes));
        credential.setCreatedAt(now);
        credential.setCreatedBy(createdBy);
        DeviceAgentCredential saved = credentialRepository.save(credential);
        return new AgentCredentialIssue(rawToken, saved);
    }

    public <T> T requiredTransaction(Supplier<T> action) {
        T value = transactionTemplate.execute(_ -> action.get());
        return Objects.requireNonNull(value, "transaction returned null");
    }

    public record AgentCredentialIssue(String rawToken, DeviceAgentCredential savedCredential) {
    }
}
