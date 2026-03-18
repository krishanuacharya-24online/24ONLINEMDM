package com.e24online.mdm.service;

import com.e24online.mdm.domain.AuditEventLog;
import com.e24online.mdm.repository.AuditEventLogRepository;
import com.e24online.mdm.service.messaging.AuditEventPublisher;
import com.e24online.mdm.web.dto.AuditEventMessage;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
public class AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);
    private static final int BEST_EFFORT_MAX_IN_FLIGHT = 4096;

    private final AuditEventLogRepository auditEventLogRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Semaphore bestEffortLimiter = new Semaphore(BEST_EFFORT_MAX_IN_FLIGHT, true);
    private final ExecutorService bestEffortExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("audit-best-effort-", 0).factory()
    );

    public AuditEventService(AuditEventLogRepository auditEventLogRepository,
                             AuditEventPublisher auditEventPublisher,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.auditEventLogRepository = auditEventLogRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public void recordBestEffort(String category,
                                 String eventType,
                                 String action,
                                 String tenantId,
                                 String actor,
                                 String entityType,
                                 String entityId,
                                 String status,
                                 Object metadata) {
        if (!bestEffortLimiter.tryAcquire()) {
            log.debug("Dropping best-effort audit due to backlog category={} eventType={} action={}",
                    category, eventType, action);
            return;
        }
        try {
            bestEffortExecutor.execute(() -> {
                try {
                    record(category, eventType, action, tenantId, actor, entityType, entityId, status, metadata);
                } catch (RuntimeException ex) {
                    log.warn("Audit record failed category={} eventType={} action={} reason={}",
                            category, eventType, action, ex.getMessage());
                } finally {
                    bestEffortLimiter.release();
                }
            });
        } catch (RuntimeException ex) {
            bestEffortLimiter.release();
            log.warn("Audit scheduling failed category={} eventType={} action={} reason={}",
                    category, eventType, action, ex.getMessage());
        }
    }

    @PreDestroy
    void shutdownBestEffortExecutor() {
        bestEffortExecutor.shutdown();
    }

    public void record(String category,
                       String eventType,
                       String action,
                       String tenantId,
                       String actor,
                       String entityType,
                       String entityId,
                       String status,
                       Object metadata) {
        AuditEventLog event = new AuditEventLog();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventCategory(normalizeCategory(category));
        event.setEventType(normalizeEventType(eventType));
        event.setAction(normalizeOptional(action, 128));
        event.setTenantId(normalizeOptionalTenantId(tenantId));
        event.setActor(normalizeActor(actor));
        event.setEntityType(normalizeOptional(entityType, 128));
        event.setEntityId(normalizeOptional(entityId, 255));
        event.setStatus(normalizeStatus(status));
        event.setMetadataJson(toJson(metadata));
        event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        AuditEventMessage message = toMessage(event);
        try {
            auditEventPublisher.publish(message);
            incrementMetric(event, "success", "queue");
            return;
        } catch (RuntimeException ex) {
            log.debug("Audit publish failed, falling back to direct save: {}", ex.getMessage());
        }

        try {
            auditEventLogRepository.save(event);
            incrementMetric(event, "success", "db");
        } catch (RuntimeException ex) {
            incrementMetric(event, "failure", "db");
            throw ex;
        }
    }

    private AuditEventMessage toMessage(AuditEventLog event) {
        AuditEventMessage message = new AuditEventMessage();
        message.setSchemaVersion(AuditEventMessage.CURRENT_SCHEMA_VERSION);
        message.setEventId(event.getEventId());
        message.setEventCategory(event.getEventCategory());
        message.setEventType(event.getEventType());
        message.setAction(event.getAction());
        message.setTenantId(event.getTenantId());
        message.setActor(event.getActor());
        message.setEntityType(event.getEntityType());
        message.setEntityId(event.getEntityId());
        message.setStatus(event.getStatus());
        message.setMetadataJson(event.getMetadataJson());
        message.setCreatedAt(event.getCreatedAt());
        return message;
    }

    private void incrementMetric(AuditEventLog event, String outcome, String delivery) {
        meterRegistry.counter(
                "mdm.audit.events",
                "category", normalizeMetricTag(event.getEventCategory()),
                "event_type", normalizeMetricTag(event.getEventType()),
                "action", normalizeMetricTag(event.getAction()),
                "status", normalizeMetricTag(event.getStatus()),
                "scope", event.getTenantId() == null ? "global" : "tenant",
                "outcome", normalizeMetricTag(outcome),
                "delivery", normalizeMetricTag(delivery)
        ).increment();
    }

    private String toJson(Object metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("serialization_error", true);
            fallback.put("type", metadata.getClass().getName());
            fallback.put("message", ex.getMessage());
            try {
                return objectMapper.writeValueAsString(fallback);
            } catch (Exception _) {
                return "{\"serialization_error\":true}";
            }
        }
    }

    private String normalizeCategory(String value) {
        return normalizeRequiredEnumLike(value, "SYSTEM");
    }

    private String normalizeEventType(String value) {
        return normalizeRequiredEnumLike(value, "GENERIC_EVENT");
    }

    private String normalizeStatus(String value) {
        return normalizeRequiredEnumLike(value, "SUCCESS");
    }

    private String normalizeActor(String value) {
        String normalized = normalizeOptional(value, 255);
        return normalized == null ? "system" : normalized;
    }

    private String normalizeOptionalTenantId(String value) {
        String normalized = normalizeOptional(value, 255);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredEnumLike(String value, String fallback) {
        String normalized = normalizeOptional(value, 128);
        if (normalized == null) {
            return fallback;
        }
        return normalized
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_");
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

    private String normalizeMetricTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
    }
}
