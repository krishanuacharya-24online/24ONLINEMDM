package com.e24online.mdm.service.messaging;

import com.e24online.mdm.web.dto.PolicyAuditMessage;
import com.e24online.mdm.web.dto.PolicyAuditQueueProperties;
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
public class PolicyAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(PolicyAuditPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final PolicyAuditQueueProperties queueProperties;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public PolicyAuditPublisher(RabbitTemplate rabbitTemplate,
                                PolicyAuditQueueProperties queueProperties,
                                Validator validator,
                                ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueProperties = queueProperties;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public void publish(PolicyAuditMessage message) {
        validateSchema(message);

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to serialize policy audit message", ex);
        }

        rabbitTemplate.convertAndSend(queueProperties.getExchange(), queueProperties.getRoutingKey(), payload, amqpMessage -> {
            amqpMessage.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            amqpMessage.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
            return amqpMessage;
        });

        log.info("Published policy audit message eventId={} policyType={} operation={} policyId={}",
                message.getEventId(),
                message.getPolicyType(),
                message.getOperation(),
                message.getPolicyId());
    }

    private void validateSchema(PolicyAuditMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Policy audit message is required");
        }
        Set<ConstraintViolation<PolicyAuditMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid policy audit message schema: " + detail);
        }
        if (!Objects.equals(message.getSchemaVersion(), PolicyAuditMessage.CURRENT_SCHEMA_VERSION)) {
            throw new IllegalArgumentException("Unsupported policy audit schema version: " + message.getSchemaVersion());
        }
    }
}
