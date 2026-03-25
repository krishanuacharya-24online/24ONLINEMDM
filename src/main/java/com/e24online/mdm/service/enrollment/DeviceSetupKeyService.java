package com.e24online.mdm.service;

import com.e24online.mdm.constants.DeviceEnrollmentServiceConstants;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.DeviceSetupKey;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.SetupKeyIssue;
import com.e24online.mdm.repository.DeviceSetupKeyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
class DeviceSetupKeyService {

    private final DeviceSetupKeyRepository setupKeyRepository;
    private final DeviceEnrollmentSupport support;

    DeviceSetupKeyService(DeviceSetupKeyRepository setupKeyRepository,
                          DeviceEnrollmentSupport support) {
        this.setupKeyRepository = setupKeyRepository;
        this.support = support;
    }

    SetupKeyIssue createSetupKey(String tenantId,
                                 Long issuedByUserId,
                                 Long targetUserId,
                                 String actor,
                                 Integer maxUses,
                                 Integer ttlMinutes) {
        Tenant activeTenant = support.requireActiveTenant(support.normalizeTenantId(tenantId), HttpStatus.FORBIDDEN);
        String normalizedTenant = activeTenant.getTenantId();
        AuthUser issuer = support.requireActiveTenantUser(activeTenant.getId(), issuedByUserId, HttpStatus.FORBIDDEN, "issuer_user_id");
        AuthUser targetUser = support.requireActiveTenantUser(activeTenant.getId(), targetUserId, HttpStatus.BAD_REQUEST, "target_user_id");
        String effectiveActor = support.normalizeActor(actor);
        int safeMaxUses = support.normalizeBounded(maxUses, DeviceEnrollmentServiceConstants.DEFAULT_SETUP_KEY_MAX_USES, 1, 1000, "max_uses");
        int safeTtlMinutes = support.normalizeBounded(ttlMinutes, DeviceEnrollmentServiceConstants.DEFAULT_SETUP_KEY_TTL_MINUTES, 1, 7 * 24 * 60, "ttl_minutes");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusMinutes(safeTtlMinutes);
        String rawKey = support.generateSetupCode();

        DeviceSetupKey entity = new DeviceSetupKey();
        entity.setTenantId(normalizedTenant);
        entity.setKeyHash(support.sha256Hex(rawKey));
        entity.setKeyHint(support.mask(rawKey));
        entity.setStatus(DeviceEnrollmentServiceConstants.ACTIVE);
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

        return new SetupKeyIssue(
                saved.getId(),
                rawKey,
                saved.getKeyHint(),
                saved.getExpiresAt(),
                saved.getMaxUses(),
                saved.getTargetUserId(),
                saved.getIssuedByUserId()
        );
    }
}
