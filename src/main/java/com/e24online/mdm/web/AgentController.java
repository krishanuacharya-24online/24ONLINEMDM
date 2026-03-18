package com.e24online.mdm.web;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantApiKey;
import com.e24online.mdm.records.cache.CachedTenantAccess;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.TenantApiKeyRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.DeviceEnrollmentService;
import com.e24online.mdm.service.WorkflowOrchestrationService;
import com.e24online.mdm.web.dto.DecisionAckRequest;
import com.e24online.mdm.web.dto.DecisionAckResponse;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("${api.version.prefix:v1}")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    private static final Set<String> PROCESS_STATUSES =
            Set.of("RECEIVED", "QUEUED", "VALIDATED", "EVALUATED", "FAILED");
    private static final Set<String> DELIVERY_STATUSES =
            Set.of("PENDING", "SENT", "ACKED", "FAILED", "TIMEOUT");
    private static final int TENANT_KEY_LOOKBACK_LIMIT = 5;

    private final WorkflowOrchestrationService workflowService;
    private final DevicePosturePayloadRepository payloadRepository;
    private final DeviceDecisionResponseRepository decisionRepository;
    private final TenantRepository tenantRepository;
    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceEnrollmentService enrollmentService;
    private final BlockingDb blockingDb;
    private final Duration tenantKeyRotationGrace;
    private final Duration tenantKeyValidationCacheTtl;
    private final Map<String, CachedTenantAccess> tenantAccessCache = new ConcurrentHashMap<>();

    public AgentController(WorkflowOrchestrationService workflowService,
                           DevicePosturePayloadRepository payloadRepository,
                           DeviceDecisionResponseRepository decisionRepository,
                           TenantRepository tenantRepository,
                           TenantApiKeyRepository tenantApiKeyRepository,
                           PasswordEncoder passwordEncoder,
                           DeviceEnrollmentService enrollmentService,
                           BlockingDb blockingDb,
                           @org.springframework.beans.factory.annotation.Value("${security.tenant-key.rotation-grace-seconds:300}") long tenantKeyRotationGraceSeconds,
                           @org.springframework.beans.factory.annotation.Value("${security.tenant-key.validation-cache-seconds:15}") long tenantKeyValidationCacheSeconds) {
        this.workflowService = workflowService;
        this.payloadRepository = payloadRepository;
        this.decisionRepository = decisionRepository;
        this.tenantRepository = tenantRepository;
        this.tenantApiKeyRepository = tenantApiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.enrollmentService = enrollmentService;
        this.blockingDb = blockingDb;
        this.tenantKeyRotationGrace = Duration.ofSeconds(Math.max(0L, tenantKeyRotationGraceSeconds));
        this.tenantKeyValidationCacheTtl = Duration.ofSeconds(Math.max(0L, tenantKeyValidationCacheSeconds));
    }

    @PostMapping("/agent/posture-payloads")
    public Mono<ResponseEntity<PosturePayloadIngestResponse>> ingestPosturePayload(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Tenant-Key", required = false) String tenantKey,
            @RequestHeader(name = "X-Device-Token", required = false) String deviceToken,
            ServerHttpRequest httpRequest,
            @Valid @RequestBody Mono<PosturePayloadIngestRequest> request
    ) {
        String ingestPath = httpRequest.getPath().value();
        String normalizedDeviceToken = normalizeOptionalText(deviceToken);
        log.debug("Agent posture payload request received: hasTenantId={}, hasTenantKey={}, hasDeviceToken={}",
                normalizeOptionalText(tenantId) != null,
                normalizeOptionalText(tenantKey) != null,
                normalizedDeviceToken != null);

        if (normalizedDeviceToken != null) {
            return request.flatMap(req -> enrollmentService
                            .authenticateDeviceTokenAsync(normalizedDeviceToken)
                            .flatMap(principal -> {
                                String normalizedDeviceId = normalizeRequiredText(req.getDeviceExternalId(),
                                        "device_external_id");
                                if (!principal.enrollmentNo().equals(normalizedDeviceId)) {
                                    return Mono.error(new ResponseStatusException(
                                            HttpStatus.UNAUTHORIZED,
                                            "device_external_id does not match X-Device-Token enrollment"
                                    ));
                                }
                                return workflowService.ingestAndQueueAsync(principal.tenantId(), req);
                            }))
                    .map(response -> ResponseEntity.ok(withResultStatusUrl(response, ingestPath)));
        }

        String normalizedTenantId = normalizeTenantId(tenantId);
        return validateTenantAccess(normalizedTenantId, tenantKey)
                .then(request.flatMap(req -> {
                    String normalizedDeviceId = normalizeRequiredText(req.getDeviceExternalId(),
                            "device_external_id");
                    return enrollmentService.ensureActiveEnrollmentAsync(normalizedTenantId, normalizedDeviceId)
                            .then(workflowService.ingestAndQueueAsync(normalizedTenantId, req));
                }))
                .map(response -> ResponseEntity.ok(withResultStatusUrl(response, ingestPath)));
    }

    @GetMapping("/agent/posture-payloads")
    public Flux<DevicePosturePayload> listPosturePayloads(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Tenant-Key", required = false) String tenantKey,
            @RequestParam(name = "device_external_id", required = false) String deviceExternalId,
            @RequestParam(name = "process_status", required = false) String processStatus,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String normalizedDeviceId = normalizeOptionalText(deviceExternalId);
        String normalizedProcessStatus = normalizeProcessStatus(processStatus);
        int safeSize = normalizePageSize(size);
        int safePage = normalizePage(page);
        long offset = (long) safePage * safeSize;

        return validateTenantAccess(normalizedTenantId, tenantKey)
                .thenMany(blockingDb.flux(() -> payloadRepository.findPaged(
                        normalizedTenantId, normalizedDeviceId, normalizedProcessStatus, safeSize, offset)));
    }

    @GetMapping("/agent/posture-payloads/{payload_id}")
    public Mono<DevicePosturePayload> getPosturePayload(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Tenant-Key", required = false) String tenantKey,
            @PathVariable("payload_id") Long payloadId
    ) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        return validateTenantAccess(normalizedTenantId, tenantKey)
                .then(blockingDb.mono(() -> payloadRepository.findByIdAndTenant(payloadId, normalizedTenantId))
                        .flatMap(Mono::justOrEmpty)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"))));
    }

    @GetMapping("/agent/posture-payloads/{payload_id}/result")
    public Mono<PosturePayloadIngestResponse> getPosturePayloadResult(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Tenant-Key", required = false) String tenantKey,
            ServerHttpRequest httpRequest,
            @PathVariable("payload_id") Long payloadId
    ) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String resultPath = httpRequest.getPath().value();
        return validateTenantAccess(normalizedTenantId, tenantKey)
                .then(workflowService.getPayloadResultAsync(normalizedTenantId, payloadId))
                .map(response -> withResultStatusUrl(response, resultPath));
    }

    @GetMapping("/agent/devices/{device_external_id}/decision/latest")
    public Mono<DeviceDecisionResponse> getLatestDecision(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Tenant-Key", required = false) String tenantKey,
            @PathVariable("device_external_id") String deviceExternalId
    ) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId, "device_external_id");
        return validateTenantAccess(normalizedTenantId, tenantKey)
                .then(blockingDb.mono(() -> decisionRepository.findLatestByDevice(normalizedTenantId, normalizedDeviceId))
                        .flatMap(Mono::justOrEmpty)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision response not found"))));
    }

    @PostMapping("/agent/decision-responses/{response_id}/ack")
    public Mono<DecisionAckResponse> acknowledgeDecision(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Tenant-Key", required = false) String tenantKey,
            @PathVariable("response_id") Long responseId,
            @Valid @RequestBody Mono<DecisionAckRequest> request
    ) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        return validateTenantAccess(normalizedTenantId, tenantKey)
                .then(request.flatMap(req ->
                        blockingDb.mono(() -> decisionRepository.findByIdAndTenant(responseId, normalizedTenantId))
                                .flatMap(Mono::justOrEmpty)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision response not found")))
                                .flatMap(existing -> {
                                    String deliveryStatus = normalizeDeliveryStatus(req.getDeliveryStatus());
                                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                                    OffsetDateTime sentAt = existing.getSentAt() != null
                                            ? existing.getSentAt() : now;
                                    if ("SENT".equals(deliveryStatus) && existing.getSentAt() == null) {
                                        existing.setSentAt(sentAt);
                                    }

                                    OffsetDateTime acknowledgedAt = req.getAcknowledgedAt() != null
                                            ? req.getAcknowledgedAt()
                                            : existing.getAcknowledgedAt();
                                    if ("ACKED".equals(deliveryStatus) && acknowledgedAt == null) {
                                        acknowledgedAt = now;
                                    }
                                    if (acknowledgedAt != null && acknowledgedAt.isBefore(sentAt)) {
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "acknowledged_at cannot be before sent_at"
                                        ));
                                    }

                                    existing.setDeliveryStatus(deliveryStatus);
                                    existing.setAcknowledgedAt(acknowledgedAt);
                                    existing.setErrorMessage(truncate(req.getErrorMessage()));
                                    return blockingDb.mono(() -> decisionRepository.save(existing));
                                })
                                .map(saved -> new DecisionAckResponse(
                                        saved.getId(),
                                        saved.getDeliveryStatus(),
                                        saved.getAcknowledgedAt()
                                ))));
    }

    private Mono<Void> validateTenantAccess(String tenantId, String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Tenant-Key"));
        }
        String providedKey = tenantKey.trim();
        String cacheKey = tenantAccessCacheKey(tenantId, providedKey);
        if (isTenantAccessCached(cacheKey)) {
            return Mono.empty();
        }

        return blockingDb.mono(() -> {
            Tenant tenant = tenantRepository.findActiveByTenantId(tenantId).orElse(null);
            if (tenant == null || !"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid tenant");
            }

            Optional<TenantApiKey> activeKey = tenantApiKeyRepository.findActiveByTenantMasterId(tenant.getId());
            if (activeKey.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No active tenant key configured");
            }

            TenantApiKey key = activeKey.get();
            if (!passwordEncoder.matches(providedKey, key.getKeyHash())) {
                if (!matchesGraceKey(tenant.getId(), providedKey)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Tenant-Key");
                }
                cacheTenantAccess(cacheKey);
                return true;
            }
            if (passwordEncoder.upgradeEncoding(key.getKeyHash())) {
                key.setKeyHash(passwordEncoder.encode(providedKey));
                tenantApiKeyRepository.save(key);
            }
            cacheTenantAccess(cacheKey);
            return true;
        }).then();
    }

    private boolean matchesGraceKey(Long tenantMasterId, String providedKey) {
        if (tenantMasterId == null || tenantKeyRotationGrace.isZero()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (TenantApiKey candidate : tenantApiKeyRepository.findRecentByTenantMasterId(tenantMasterId, TENANT_KEY_LOOKBACK_LIMIT)) {
            if (!"REVOKED".equalsIgnoreCase(candidate.getStatus())) {
                continue;
            }
            OffsetDateTime revokedAt = candidate.getRevokedAt();
            if (revokedAt == null || revokedAt.plus(tenantKeyRotationGrace).isBefore(now)) {
                continue;
            }
            if (passwordEncoder.matches(providedKey, candidate.getKeyHash())) {
                return true;
            }
        }
        return false;
    }

    private String tenantAccessCacheKey(String tenantId, String providedKey) {
        return tenantId + ":" + sha256Hex(providedKey);
    }

    private boolean isTenantAccessCached(String cacheKey) {
        if (tenantKeyValidationCacheTtl.isZero() || tenantKeyValidationCacheTtl.isNegative()) {
            return false;
        }
        CachedTenantAccess cached = tenantAccessCache.get(cacheKey);
        if (cached == null) {
            return false;
        }
        if (cached.expiresAtEpochMillis() > System.currentTimeMillis()) {
            return true;
        }
        tenantAccessCache.remove(cacheKey, cached);
        return false;
    }

    private void cacheTenantAccess(String cacheKey) {
        if (tenantKeyValidationCacheTtl.isZero() || tenantKeyValidationCacheTtl.isNegative()) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + tenantKeyValidationCacheTtl.toMillis();
        tenantAccessCache.put(cacheKey, new CachedTenantAccess(expiresAt));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String normalizeTenantId(String rawTenantId) {
        String normalized = rawTenantId == null ? "" : rawTenantId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Tenant-Id");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeProcessStatus(String processStatus) {
        String value = normalizeOptionalText(processStatus);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!PROCESS_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid process_status");
        }
        return normalized;
    }

    private String normalizeDeliveryStatus(String deliveryStatus) {
        String value = normalizeRequiredText(deliveryStatus, "delivery_status");
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!DELIVERY_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid delivery_status");
        }
        return normalized;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= AgentController.MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, AgentController.MAX_ERROR_MESSAGE_LENGTH);
    }

    private PosturePayloadIngestResponse withResultStatusUrl(PosturePayloadIngestResponse response, String path) {
        if (response == null) {
            return null;
        }
        if (path == null || path.isBlank()) {
            return response;
        }

        String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (normalizedPath.endsWith("/result")) {
            response.setResultStatusUrl(normalizedPath);
            return response;
        }

        if (response.getPayloadId() != null) {
            response.setResultStatusUrl(normalizedPath + "/" + response.getPayloadId() + "/result");
        }
        return response;
    }

}
