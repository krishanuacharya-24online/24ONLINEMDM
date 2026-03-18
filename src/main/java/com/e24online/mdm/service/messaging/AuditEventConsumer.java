package com.e24online.mdm.service.messaging;

import com.e24online.mdm.domain.AuditEventLog;
import com.e24online.mdm.repository.AuditEventLogRepository;
import com.e24online.mdm.web.dto.AuditEventMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventLogRepository auditEventLogRepository;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditEventLogRepository auditEventLogRepository,
                              Validator validator,
                              ObjectMapper objectMapper) {
        this.auditEventLogRepository = auditEventLogRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(
            queues = "${mdm.audit.queue.queue}",
            containerFactory = "auditListenerContainerFactory"
    )
    public void consume(byte[] rawBody) {
        String rawMessage = rawBody == null ? null : new String(rawBody, StandardCharsets.UTF_8);
        AuditEventMessage message = parseAndValidate(rawMessage);

        AuditEventLog audit = getAuditEventLog(message);

        auditEventLogRepository.save(audit);
        log.debug("Persisted audit event eventId={} category={} type={} status={}",
                message.getEventId(),
                message.getEventCategory(),
                message.getEventType(),
                message.getStatus());
    }

    private static @NonNull AuditEventLog getAuditEventLog(AuditEventMessage message) {
        AuditEventLog audit = new AuditEventLog();
        audit.setEventId(message.getEventId());
        audit.setEventCategory(message.getEventCategory());
        audit.setEventType(message.getEventType());
        audit.setAction(message.getAction());
        audit.setTenantId(message.getTenantId());
        audit.setActor(message.getActor());
        audit.setEntityType(message.getEntityType());
        audit.setEntityId(message.getEntityId());
        audit.setStatus(message.getStatus());
        audit.setMetadataJson(message.getMetadataJson());
        audit.setCreatedAt(message.getCreatedAt());
        return audit;
    }

    private AuditEventMessage parseAndValidate(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw reject("Received empty audit event message");
        }

        final AuditEventMessage message;
        try {
            message = objectMapper.readValue(rawMessage, AuditEventMessage.class);
        } catch (JacksonException _) {
            throw reject("Invalid audit event message JSON");
        }

        Set<ConstraintViolation<AuditEventMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw reject("Invalid audit event message schema: " + detail);
        }

        if (!Objects.equals(message.getSchemaVersion(), AuditEventMessage.CURRENT_SCHEMA_VERSION)) {
            throw reject("Unsupported audit event schema_version=" + message.getSchemaVersion());
        }

        return message;
    }

    private AmqpRejectAndDontRequeueException reject(String reason) {
        log.warn(reason);
        return new AmqpRejectAndDontRequeueException(reason);
    }
}

