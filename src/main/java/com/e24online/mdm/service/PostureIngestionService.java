package com.e24online.mdm.service;

import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.records.IngestionResult;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.utils.TextSanitizer;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import io.github.resilience4j.retry.annotation.Retry;
import org.jspecify.annotations.NonNull;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service for ingesting device posture payloads.
 * Handles validation, normalization, and persistence of raw posture data.
 */
@Service
public class PostureIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PostureIngestionService.class);
    private static final int MAX_DEVICE_EXTERNAL_ID_LENGTH = 255;
    private static final int MAX_AGENT_ID_LENGTH = 255;
    private static final int MAX_AGENT_VERSION_LENGTH = 128;
    private static final int MAX_PAYLOAD_VERSION_LENGTH = 64;
    private static final int MAX_PAYLOAD_HASH_LENGTH = 512;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;
    private static final int MAX_PAYLOAD_JSON_BYTES = 1_000_000;
    private static final Set<String> VERIFIED_PAYLOAD_VERSIONS = Set.of("v1", "1.0");

    private final DevicePosturePayloadRepository repository;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;
    private final Scheduler jdbcScheduler;
    private final TenantEntitlementService tenantEntitlementService;

    public PostureIngestionService(DevicePosturePayloadRepository repository,
                                   AuditEventService auditEventService,
                                   ObjectMapper objectMapper,
                                   Scheduler jdbcScheduler,
                                   TenantEntitlementService tenantEntitlementService) {
        this.repository = repository;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
        this.jdbcScheduler = jdbcScheduler;
        this.tenantEntitlementService = tenantEntitlementService;
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
            String agentVersion = normalizeOptional(request.getAgentVersion(), MAX_AGENT_VERSION_LENGTH);
            String payloadHashInput = normalizeOptional(request.getPayloadHash(), MAX_PAYLOAD_HASH_LENGTH);

            JsonNode payloadNode = request.getPayloadJson();
            if (payloadNode == null || payloadNode.isNull()) {
                throw new IllegalArgumentException("payload_json is required");
            }
            if (!payloadNode.isObject()) {
                throw new IllegalArgumentException("payload_json must be a JSON object");
            }
            payloadNode = TextSanitizer.sanitizeJsonNode(payloadNode);

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            String serialized;
            serialized = getSerialized(payloadNode);
            ContractMetadata metadata = resolveContractMetadata(request, payloadNode, payloadVersion, agentVersion, now);

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

            tenantEntitlementService.assertCanIngestPayload(normalizedTenantId);
            DevicePosturePayload payload = new DevicePosturePayload();

            payload.setTenantId(normalizedTenantId);
            payload.setDeviceExternalId(deviceExternalId);
            payload.setAgentId(agentId);
            payload.setPayloadVersion(payloadVersion);
            payload.setCaptureTime(metadata.captureTime());
            payload.setAgentVersion(agentVersion);
            payload.setAgentCapabilities(metadata.agentCapabilitiesJson());
            payload.setPayloadHash(payloadHash);
            payload.setIdempotencyKey(idempotencyKey);
            payload.setPayloadJson(serialized);
            payload.setSchemaCompatibilityStatus(metadata.schemaCompatibilityStatus());
            payload.setValidationWarnings(toJson(metadata.validationWarnings()));
            payload.setProcessStatus("RECEIVED");
            payload.setProcessError(null);
            payload.setProcessedAt(null);
            payload.setReceivedAt(now);
            payload.setCreatedAt(now);
            payload.setCreatedBy("agent-ingest");

            return getIngestionResult(deviceExternalId, normalizedTenantId, payload, idempotencyKey);
        } catch (RuntimeException ex) {
            auditIngestionEvent("FAILURE", normalizedTenantId, deviceExternalId, null, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private @NonNull String getSerialized(JsonNode payloadNode) {
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(payloadNode);
            if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_JSON_BYTES) {
                throw new IllegalArgumentException("payload_json exceeds max allowed size");
            }
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid payload_json", e);
        }
        return serialized;
    }

    private @NonNull IngestionResult getIngestionResult(String deviceExternalId, String normalizedTenantId, DevicePosturePayload payload, String idempotencyKey) {
        try {
            log.debug("Payload mode: INSERT_NEW for device: {} tenant: {}", deviceExternalId, normalizedTenantId);
            DevicePosturePayload saved = repository.save(payload);
            tenantEntitlementService.recordPayloadAccepted(normalizedTenantId, saved.getReceivedAt());
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
    }

    private ContractMetadata resolveContractMetadata(PosturePayloadIngestRequest request,
                                                     JsonNode payloadNode,
                                                     String payloadVersion,
                                                     String agentVersion,
                                                     OffsetDateTime now) {
        List<String> validationWarnings = new ArrayList<>();
        OffsetDateTime captureTime = resolveCaptureTime(request.getCaptureTime(), payloadNode, now, validationWarnings);
        JsonNode capabilitiesNode = request.getAgentCapabilities();
        String capabilitiesJson = capabilitiesNode == null || capabilitiesNode.isNull()
                ? "[]"
                : toJson(capabilitiesNode);

        if (agentVersion == null) {
            validationWarnings.add("agent_version was not supplied");
        }
        if (capabilitiesNode == null || capabilitiesNode.isNull()
                || (capabilitiesNode.isArray() && capabilitiesNode.isEmpty())
                || (capabilitiesNode.isObject() && capabilitiesNode.isEmpty())) {
            validationWarnings.add("agent_capabilities was not supplied");
        }

        String normalizedVersion = payloadVersion.trim().toLowerCase(Locale.ROOT);
        if (!VERIFIED_PAYLOAD_VERSIONS.contains(normalizedVersion)) {
            validationWarnings.add("payload_version is outside the verified compatibility set");
            return new ContractMetadata(captureTime, capabilitiesJson, "UNVERIFIED", validationWarnings);
        }
        return new ContractMetadata(
                captureTime,
                capabilitiesJson,
                validationWarnings.isEmpty() ? "SUPPORTED" : "SUPPORTED_WITH_WARNINGS",
                validationWarnings
        );
    }

    private OffsetDateTime resolveCaptureTime(OffsetDateTime requestCaptureTime,
                                              JsonNode payloadNode,
                                              OffsetDateTime fallback,
                                              List<String> validationWarnings) {
        if (requestCaptureTime != null) {
            return requestCaptureTime;
        }
        JsonNode payloadCaptureTime = payloadNode.get("capture_time");
        if (payloadCaptureTime != null && payloadCaptureTime.isTextual()) {
            try {
                return OffsetDateTime.parse(payloadCaptureTime.asText().trim());
            } catch (DateTimeParseException _) {
                validationWarnings.add("payload_json.capture_time could not be parsed");
            }
        }
        validationWarnings.add("capture_time was not supplied; received_at was used");
        return fallback;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid JSON value", ex);
        }
    }

    private record ContractMetadata(
            OffsetDateTime captureTime,
            String agentCapabilitiesJson,
            String schemaCompatibilityStatus,
            List<String> validationWarnings
    ) {
    }

}
