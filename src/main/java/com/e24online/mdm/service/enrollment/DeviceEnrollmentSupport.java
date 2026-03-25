package com.e24online.mdm.service.enrollment;

import com.e24online.mdm.constants.DeviceEnrollmentServiceConstants;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.DeviceEnrollment;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.DeviceEnrollmentRepository;
import com.e24online.mdm.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class DeviceEnrollmentSupport {

    private final TenantRepository tenantRepository;
    private final AuthUserRepository authUserRepository;
    private final DeviceEnrollmentRepository enrollmentRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceEnrollmentSupport(TenantRepository tenantRepository,
                            AuthUserRepository authUserRepository,
                            DeviceEnrollmentRepository enrollmentRepository) {
        this.tenantRepository = tenantRepository;
        this.authUserRepository = authUserRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public int normalizePageSize(int size) {
        if (size <= 0) {
            return DeviceEnrollmentServiceConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, DeviceEnrollmentServiceConstants.MAX_PAGE_SIZE);
    }

    public String normalizeStatusFilter(String status) {
        String normalized = normalizeOptional(status, 64);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!DeviceEnrollmentServiceConstants.ACTIVE.equals(upper)
                && !"DE_ENROLLED".equals(upper)
                && !DeviceEnrollmentServiceConstants.EXPIRED.equals(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        return upper;
    }

    public int normalizeBounded(Integer value, int defaultValue, int min, int max, String field) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < min || resolved > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is out of range");
        }
        return resolved;
    }

    public Long normalizeOptionalPositive(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be positive");
        }
        return value;
    }

    public Long normalizeRequiredPositive(Long value, String fieldName) {
        Long normalized = normalizeOptionalPositive(value, fieldName);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return normalized;
    }

    public String normalizeTenantId(String tenantId) {
        String normalized = normalizeRequired(tenantId, "tenant_id", 64).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id is required");
        }
        return normalized;
    }

    public String normalizeActor(String actor) {
        String normalized = normalizeOptional(actor, 128);
        return normalized == null ? "system" : normalized;
    }

    public String normalizeSetupLikeToken(String value, String fieldName) {
        String normalized = normalizeRequired(value, fieldName, 512);
        String compact = normalized.replace("-", "").replace(" ", "");
        if (DeviceEnrollmentServiceConstants.COMPACT_SETUP_CODE.matcher(compact).matches()
                || DeviceEnrollmentServiceConstants.GROUPED_SETUP_CODE.matcher(normalized).matches()) {
            String upper = compact.toUpperCase(Locale.ROOT);
            return upper.substring(0, 3) + "-"
                    + upper.substring(3, 6) + "-"
                    + upper.substring(6, 9) + "-"
                    + upper.substring(9, 12);
        }
        return normalized;
    }

    public String normalizeRequired(String value, String fieldName, int maxLen) {
        String normalized = normalizeOptional(value, maxLen);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return normalized;
    }

    public String normalizeOptional(String value, int maxLen) {
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

    public Tenant requireActiveTenant(String tenantId, HttpStatus status) {
        return tenantRepository.findActiveByTenantId(tenantId)
                .filter(t -> !t.isDeleted() && DeviceEnrollmentServiceConstants.ACTIVE.equalsIgnoreCase(t.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(status, "Invalid tenant"));
    }

    public AuthUser requireActiveTenantUser(Long tenantMasterId, Long userId, HttpStatus status, String fieldName) {
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

    public void enforceOwnerScope(DeviceEnrollment enrollment, Long requiredOwnerUserId) {
        if (requiredOwnerUserId == null || requiredOwnerUserId.equals(enrollment.getOwnerUserId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, DeviceEnrollmentServiceConstants.ENROLLMENT_NOT_FOUND);
    }

    public String generateSecret(String prefix) {
        byte[] randomBytes = new byte[36];
        secureRandom.nextBytes(randomBytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return prefix + "_" + token;
    }

    public String generateSetupCode() {
        String raw = randomAllNum(DeviceEnrollmentServiceConstants.SETUP_CODE_RAW_LENGTH);
        return raw.substring(0, 3) + "-"
                + raw.substring(3, 6) + "-"
                + raw.substring(6, 9) + "-"
                + raw.substring(9, 12);
    }

    public String generateEnrollmentNo(String tenantId) {
        String cleanedTenant = tenantId.replaceAll("[^a-z0-9]", "").toUpperCase(Locale.ROOT);
        if (cleanedTenant.isBlank()) {
            cleanedTenant = "TENANT";
        }
        if (cleanedTenant.length() > 8) {
            cleanedTenant = cleanedTenant.substring(0, 8);
        }

        for (int i = 0; i < 12; i++) {
            String suffix = randomAllNum(DeviceEnrollmentServiceConstants.ENROLLMENT_NO_SUFFIX_LENGTH);
            String candidate = "ENR-" + cleanedTenant + "-" + suffix;
            Optional<DeviceEnrollment> existing = enrollmentRepository.findByTenantAndEnrollmentNo(tenantId, candidate);
            if (existing.isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate enrollment number");
    }

    public String randomAllNum(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int idx = secureRandom.nextInt(DeviceEnrollmentServiceConstants.ALL_CHAR_NUM.length());
            sb.append(DeviceEnrollmentServiceConstants.ALL_CHAR_NUM.charAt(idx));
        }
        return sb.toString();
    }

    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash value", ex);
        }
    }

    public String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "hidden";
        }
        if (raw.length() <= 14) {
            return raw;
        }
        return raw.substring(0, 6) + "..." + raw.substring(raw.length() - 6);
    }
}
