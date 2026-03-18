package com.e24online.mdm.service;

import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantApiKey;
import com.e24online.mdm.records.tenant.TenantKeyMetadataResponse;
import com.e24online.mdm.records.tenant.TenantKeyRotateResponse;
import com.e24online.mdm.records.tenant.TenantResponse;
import com.e24online.mdm.repository.TenantApiKeyRepository;
import com.e24online.mdm.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class TenantAdminService {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final TenantRepository tenantRepository;
    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final BlockingDb blockingDb;
    private final TransactionTemplate transactionTemplate;
    private final AuditEventService auditEventService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TenantAdminService(TenantRepository tenantRepository,
                              TenantApiKeyRepository tenantApiKeyRepository,
                              PasswordEncoder passwordEncoder,
                              BlockingDb blockingDb,
                              TransactionTemplate transactionTemplate,
                              AuditEventService auditEventService) {
        this.tenantRepository = tenantRepository;
        this.tenantApiKeyRepository = tenantApiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.blockingDb = blockingDb;
        this.transactionTemplate = transactionTemplate;
        this.auditEventService = auditEventService;
    }

    public Flux<TenantResponse> listTenants(int page, int size) {
        return listTenants(page, size, null);
    }

    public Flux<TenantResponse> listTenants(int page, int size, String actor) {
        int safeSize = size <= 0 ? 50 : Math.min(size, 500);
        int safePage = Math.max(page, 0);
        long offset = (long) safePage * safeSize;
        String effectiveActor = normalizeActor(actor);
        return blockingDb.flux(() -> {
                    java.util.List<Tenant> rows = tenantRepository.findPaged(safeSize, offset);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("page", safePage);
                    metadata.put("size", safeSize);
                    metadata.put("resultCount", rows.size());
                    recordAudit("TENANT_LIST_VIEWED", "LIST", null, effectiveActor, null, metadata);
                    return rows;
                })
                .map(this::toTenantResponse);
    }

    public Mono<TenantResponse> createTenant(String actor, String tenantId, String name, String status) {
        return blockingDb.mono(() -> {
            String normalizedTenantId = normalizeTenantId(tenantId);
            String normalizedName = normalizeName(name);
            String normalizedStatus = normalizeStatus(status, "ACTIVE");
            String effectiveActor = normalizeActor(actor);

            Tenant existing = tenantRepository.findByTenantId(normalizedTenantId).orElse(null);
            OffsetDateTime now = OffsetDateTime.now();
            if (existing != null) {
                if (!existing.isDeleted()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant ID already exists");
                }
                existing.setTenantId(normalizedTenantId);
                existing.setName(normalizedName);
                existing.setStatus(normalizedStatus);
                existing.setDeleted(false);
                existing.setModifiedAt(now);
                existing.setModifiedBy(effectiveActor);
                Tenant saved = tenantRepository.save(existing);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("name", saved.getName());
                metadata.put("status", saved.getStatus());
                metadata.put("revived", true);
                recordAudit(
                        "TENANT_RESTORED",
                        "RESTORE",
                        saved.getTenantId(),
                        effectiveActor,
                        saved.getId(),
                        metadata
                );
                return toTenantResponse(saved);
            }

            Tenant tenant = new Tenant();
            tenant.setTenantId(normalizedTenantId);
            tenant.setName(normalizedName);
            tenant.setStatus(normalizedStatus);
            tenant.setDeleted(false);
            tenant.setCreatedAt(now);
            tenant.setCreatedBy(effectiveActor);
            tenant.setModifiedAt(now);
            tenant.setModifiedBy(effectiveActor);
            Tenant saved = tenantRepository.save(tenant);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("name", saved.getName());
            metadata.put("status", saved.getStatus());
            metadata.put("revived", false);
            recordAudit(
                    "TENANT_CREATED",
                    "CREATE",
                    saved.getTenantId(),
                    effectiveActor,
                    saved.getId(),
                    metadata
            );
            return toTenantResponse(saved);
        });
    }

    public Mono<TenantResponse> updateTenant(Long id, String actor, String tenantId, String name, String status) {
        return blockingDb.mono(() -> {
            Tenant existing = getActiveTenantById(id);
            String normalizedTenantId = normalizeTenantId(tenantId);
            String normalizedName = normalizeName(name);
            String normalizedStatus = normalizeStatus(status, existing.getStatus());
            String effectiveActor = normalizeActor(actor);

            Tenant byTenantId = tenantRepository.findByTenantId(normalizedTenantId).orElse(null);
            if (byTenantId != null && !byTenantId.getId().equals(existing.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant ID already exists");
            }

            String beforeTenantId = existing.getTenantId();
            String beforeName = existing.getName();
            String beforeStatus = existing.getStatus();
            existing.setTenantId(normalizedTenantId);
            existing.setName(normalizedName);
            existing.setStatus(normalizedStatus);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(effectiveActor);
            Tenant saved = tenantRepository.save(existing);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("beforeTenantId", beforeTenantId);
            metadata.put("beforeName", beforeName);
            metadata.put("beforeStatus", beforeStatus);
            metadata.put("afterTenantId", saved.getTenantId());
            metadata.put("afterName", saved.getName());
            metadata.put("afterStatus", saved.getStatus());
            recordAudit(
                    "TENANT_UPDATED",
                    "UPDATE",
                    saved.getTenantId(),
                    effectiveActor,
                    saved.getId(),
                    metadata
            );
            return toTenantResponse(saved);
        });
    }

    public Mono<Void> deleteTenant(Long id, String actor) {
        return blockingDb.run(() -> transactionTemplate.executeWithoutResult(status -> {
            Tenant existing = getActiveTenantById(id);
            OffsetDateTime now = OffsetDateTime.now();
            String effectiveActor = normalizeActor(actor);

            existing.setDeleted(true);
            existing.setStatus("INACTIVE");
            existing.setModifiedAt(now);
            existing.setModifiedBy(effectiveActor);
            tenantRepository.save(existing);

            revokeActiveKey(existing.getId(), effectiveActor, now);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("status", existing.getStatus());
            metadata.put("deleted", existing.isDeleted());
            recordAudit(
                    "TENANT_DELETED",
                    "DELETE",
                    existing.getTenantId(),
                    effectiveActor,
                    existing.getId(),
                    metadata
            );
        }));
    }

    public Mono<TenantKeyMetadataResponse> getActiveTenantKey(Long id) {
        return getActiveTenantKey(id, null);
    }

    public Mono<TenantKeyMetadataResponse> getActiveTenantKey(Long id, String actor) {
        return blockingDb.mono(() -> {
            Tenant tenant = getActiveTenantById(id);
            TenantApiKey activeKey = tenantApiKeyRepository.findActiveByTenantMasterId(tenant.getId()).orElse(null);
            TenantKeyMetadataResponse response;
            if (activeKey == null) {
                response = new TenantKeyMetadataResponse(
                        tenant.getId(),
                        tenant.getTenantId(),
                        false,
                        null,
                        null
                );
            } else {
                response = new TenantKeyMetadataResponse(
                        tenant.getId(),
                        tenant.getTenantId(),
                        true,
                        activeKey.getKeyHint(),
                        activeKey.getCreatedAt()
                );
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("hasActiveKey", response.active());
            metadata.put("keyHint", response.keyHint());
            recordAudit("TENANT_KEY_VIEWED", "VIEW_KEY", tenant.getTenantId(), normalizeActor(actor), tenant.getId(), metadata);
            return response;
        });
    }

    public Mono<TenantKeyRotateResponse> rotateTenantKey(Long id, String actor) {
        return blockingDb.mono(() -> transactionTemplate.execute(status -> {
            Tenant tenant = getActiveTenantById(id);
            if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant is not ACTIVE");
            }

            OffsetDateTime now = OffsetDateTime.now();
            String effectiveActor = normalizeActor(actor);
            revokeActiveKey(tenant.getId(), effectiveActor, now);

            String rawKey = generateTenantKey(tenant.getTenantId());
            TenantApiKey key = new TenantApiKey();
            key.setTenantMasterId(tenant.getId());
            key.setKeyHash(passwordEncoder.encode(rawKey));
            key.setKeyHint(buildKeyHint(rawKey));
            key.setStatus("ACTIVE");
            key.setCreatedAt(now);
            key.setCreatedBy(effectiveActor);
            TenantApiKey saved = tenantApiKeyRepository.save(key);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("keyHint", saved.getKeyHint());
            metadata.put("createdAt", saved.getCreatedAt());
            recordAudit(
                    "TENANT_KEY_ROTATED",
                    "ROTATE",
                    tenant.getTenantId(),
                    effectiveActor,
                    tenant.getId(),
                    metadata
            );

            return new TenantKeyRotateResponse(
                    tenant.getId(),
                    tenant.getTenantId(),
                    rawKey,
                    saved.getKeyHint(),
                    saved.getCreatedAt()
            );
        }));
    }

    private Tenant getActiveTenantById(Long id) {
        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null || tenant.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        return tenant;
    }

    private void revokeActiveKey(Long tenantId, String actor, OffsetDateTime revokedAt) {
        tenantApiKeyRepository.findActiveByTenantMasterId(tenantId).ifPresent(existing -> {
            existing.setStatus("REVOKED");
            existing.setRevokedAt(revokedAt);
            existing.setRevokedBy(actor);
            tenantApiKeyRepository.save(existing);
        });
    }

    private TenantResponse toTenantResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getTenantId(), tenant.getName(), tenant.getStatus());
    }

    private String normalizeTenantId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        String tenantId = raw.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "tenantId must match ^[a-z0-9][a-z0-9._-]{2,63}$"
            );
        }
        return tenantId;
    }

    private String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        String normalized = WHITESPACE.matcher(raw.trim()).replaceAll(" ");
        if (normalized.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is too long");
        }
        return normalized;
    }

    private String normalizeStatus(String raw, String defaultStatus) {
        if (raw == null || raw.isBlank()) {
            return defaultStatus;
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or INACTIVE");
        }
        return status;
    }

    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "ui";
        }
        return actor.trim();
    }

    private String generateTenantKey(String tenantId) {
        byte[] randomBytes = new byte[36];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String safeTenant = tenantId == null ? "tenant" : tenantId.replaceAll("[^a-z0-9]+", "-");
        if (safeTenant.isBlank()) {
            safeTenant = "tenant";
        }
        if (safeTenant.length() > 24) {
            safeTenant = safeTenant.substring(0, 24);
        }
        return "tk_" + safeTenant + "_" + randomPart;
    }

    private String buildKeyHint(String key) {
        if (key == null || key.isBlank()) {
            return "hidden";
        }
        if (key.length() <= 14) {
            return key;
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 6);
    }

    private void recordAudit(String eventType,
                             String action,
                             String tenantId,
                             String actor,
                             Long tenantMasterId,
                             Map<String, Object> metadata) {
        auditEventService.recordBestEffort(
                "TENANT_ADMIN",
                eventType,
                action,
                tenantId,
                actor,
                "TENANT",
                tenantMasterId == null ? null : String.valueOf(tenantMasterId),
                "SUCCESS",
                metadata
        );
    }

}
