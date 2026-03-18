package com.e24online.mdm.service.messaging;

import com.e24online.mdm.web.dto.AuditEventMessage;
import com.e24online.mdm.web.dto.AuditQueueProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final AuditQueueProperties queueProperties;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public AuditEventPublisher(RabbitTemplate rabbitTemplate,
                               AuditQueueProperties queueProperties,
                               Validator validator,
                               ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueProperties = queueProperties;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public void publish(AuditEventMessage message) {
        validateSchema(message);

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to serialize audit event message", ex);
        }

        rabbitTemplate.convertAndSend(queueProperties.getExchange(), queueProperties.getRoutingKey(), payload, amqpMessage -> {
            amqpMessage.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            amqpMessage.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
            return amqpMessage;
        });

        log.debug("Published audit event message eventId={} category={} type={} status={}",
                message.getEventId(),
                message.getEventCategory(),
                message.getEventType(),
                message.getStatus());
    }

    private void validateSchema(AuditEventMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Audit event message is required");
        }
        Set<ConstraintViolation<AuditEventMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid audit event message schema: " + detail);
        }
        if (!Objects.equals(message.getSchemaVersion(), AuditEventMessage.CURRENT_SCHEMA_VERSION)) {
            throw new IllegalArgumentException("Unsupported audit event schema version: " + message.getSchemaVersion());
        }
    }
}
