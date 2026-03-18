package com.e24online.mdm.service;

import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.records.IngestionResult;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service for ingesting device posture payloads.
 * Handles validation, normalization, and persistence of raw posture data.
 */
@Service
public class PostureIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PostureIngestionService.class);
    private static final int MAX_DEVICE_EXTERNAL_ID_LENGTH = 255;
    private static final int MAX_AGENT_ID_LENGTH = 255;
    private static final int MAX_PAYLOAD_VERSION_LENGTH = 64;
    private static final int MAX_PAYLOAD_HASH_LENGTH = 512;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;
    private static final int MAX_PAYLOAD_JSON_BYTES = 1_000_000;

    private final DevicePosturePayloadRepository repository;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;
    private final Scheduler jdbcScheduler;

    public PostureIngestionService(DevicePosturePayloadRepository repository,
                                   AuditEventService auditEventService,
                                   ObjectMapper objectMapper,
                                   Scheduler jdbcScheduler) {
        this.repository = repository;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
        this.jdbcScheduler = jdbcScheduler;
    }

    @Retry(name = "ingest")
    @Transactional
    public Long ingest(String tenantId, PosturePayloadIngestRequest request) {
        log.debug("Ingesting posture payload for tenant: {}", tenantId);
        return ingestWithResolution(tenantId, request).payload().getId();
    }

    @Retry(name = "ingest")
    @Transactional
    public IngestionResult ingestWithResolution(String tenantId, PosturePayloadIngestRequest request) {
        log.debug("Ingesting posture payload with idempotency for tenant: {}", tenantId);
        return persistPayload(tenantId, request);
    }

    public Mono<Long> ingestAsync(String tenantId, PosturePayloadIngestRequest request) {
        return Mono.fromCallable(() -> ingest(tenantId, request))
                .subscribeOn(jdbcScheduler);
    }

    private IngestionResult persistPayload(String tenantId, PosturePayloadIngestRequest request) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String deviceExternalId = null;
        try {
            deviceExternalId = normalizeRequired(request.getDeviceExternalId(), "device_external_id", MAX_DEVICE_EXTERNAL_ID_LENGTH);
            String agentId = normalizeRequired(request.getAgentId(), "agent_id", MAX_AGENT_ID_LENGTH);
            String payloadVersion = normalizeRequired(request.getPayloadVersion(), "payload_version", MAX_PAYLOAD_VERSION_LENGTH);
            String payloadHashInput = normalizeOptional(request.getPayloadHash(), MAX_PAYLOAD_HASH_LENGTH);

            JsonNode payloadNode = request.getPayloadJson();
            if (payloadNode == null || payloadNode.isNull()) {
                throw new IllegalArgumentException("payload_json is required");
            }
            if (!payloadNode.isObject()) {
                throw new IllegalArgumentException("payload_json must be a JSON object");
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            String serialized;
            try {
                serialized = objectMapper.writeValueAsString(payloadNode);
                if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_JSON_BYTES) {
                    throw new IllegalArgumentException("payload_json exceeds max allowed size");
                }
            } catch (JacksonException e) {
                throw new IllegalArgumentException("Invalid payload_json", e);
            }

            String payloadHash = payloadHashInput != null ? payloadHashInput : sha256Hex(serialized);
            String idempotencyKey = buildIdempotencyKey(normalizedTenantId, deviceExternalId, payloadHash);

            Optional<DevicePosturePayload> existing = repository.findByIdempotencyKey(
                    normalizedTenantId,
                    deviceExternalId,
                    idempotencyKey
            );
            if (existing.isPresent()) {
                log.debug("Payload mode: IDEMPOTENT_DUPLICATE for device: {} tenant: {}", deviceExternalId, normalizedTenantId);
                DevicePosturePayload duplicate = existing.get();
                auditIngestionEvent("SUCCESS", normalizedTenantId, deviceExternalId, duplicate.getId(), "IDEMPOTENT_DUPLICATE", null);
                return new IngestionResult(duplicate, false);
            }

            DevicePosturePayload payload = new DevicePosturePayload();

            payload.setTenantId(normalizedTenantId);
            payload.setDeviceExternalId(deviceExternalId);
            payload.setAgentId(agentId);
            payload.setPayloadVersion(payloadVersion);
            payload.setPayloadHash(payloadHash);
            payload.setIdempotencyKey(idempotencyKey);
            payload.setPayloadJson(serialized);
            payload.setProcessStatus("RECEIVED");
            payload.setProcessError(null);
            payload.setProcessedAt(null);
            payload.setReceivedAt(now);
            payload.setCreatedAt(now);
            payload.setCreatedBy("agent-ingest");

            try {
                log.debug("Payload mode: INSERT_NEW for device: {} tenant: {}", deviceExternalId, normalizedTenantId);
                DevicePosturePayload saved = repository.save(payload);
                auditIngestionEvent("SUCCESS", normalizedTenantId, deviceExternalId, saved.getId(), "INSERT_NEW", null);
                return new IngestionResult(saved, true);
            } catch (DataIntegrityViolationException ex) {
                // Concurrency-safe fallback when two requests race on the same idempotency key.
                Optional<DevicePosturePayload> raced = repository.findByIdempotencyKey(
                        normalizedTenantId,
                        deviceExternalId,
                        idempotencyKey
                );
                if (raced.isPresent()) {
                    DevicePosturePayload racedPayload = raced.get();
                    auditIngestionEvent("SUCCESS", normalizedTenantId, deviceExternalId, racedPayload.getId(), "IDEMPOTENT_DUPLICATE", null);
                    return new IngestionResult(racedPayload, false);
                }
                throw ex;
            }
        } catch (RuntimeException ex) {
            auditIngestionEvent("FAILURE", normalizedTenantId, deviceExternalId, null, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private void auditIngestionEvent(String status,
                                     String tenantId,
                                     String deviceExternalId,
                                     Long payloadId,
                                     String mode,
                                     String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deviceExternalId", deviceExternalId);
        metadata.put("mode", mode);
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditEventService.recordBestEffort(
                "POSTURE",
                "POSTURE_PAYLOAD_INGESTED",
                "INGEST",
                tenantId,
                "agent-ingest",
                "DEVICE_POSTURE_PAYLOAD",
                payloadId == null ? null : String.valueOf(payloadId),
                status,
                metadata
        );
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private String normalizeTenantId(String tenantId) {
        if (tenantId == null) {
            return "";
        }
        return tenantId.trim().toLowerCase(Locale.ROOT);
    }

    private String buildIdempotencyKey(String tenantId, String deviceExternalId, String payloadHash) {
        String raw = tenantId + "|" + deviceExternalId.toLowerCase(Locale.ROOT) + "|" + payloadHash.toLowerCase(Locale.ROOT);
        return truncate(sha256Hex(raw), IDEMPOTENCY_KEY_MAX_LENGTH);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
