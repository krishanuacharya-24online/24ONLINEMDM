package com.e24online.mdm.service.messaging;

import com.e24online.mdm.web.dto.PostureQueueProperties;
import com.e24online.mdm.web.dto.PostureEvaluationMessage;
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
public class PostureEvaluationPublisher {

    private static final Logger log = LoggerFactory.getLogger(PostureEvaluationPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final PostureQueueProperties queueProperties;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public PostureEvaluationPublisher(RabbitTemplate rabbitTemplate,
                                      PostureQueueProperties queueProperties,
                                      Validator validator,
                                      ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueProperties = queueProperties;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public void publish(PostureEvaluationMessage message) {
        validateSchema(message);

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to serialize posture evaluation message", ex);
        }

        rabbitTemplate.convertAndSend(queueProperties.getExchange(), queueProperties.getRoutingKey(), payload, amqpMessage -> {
            amqpMessage.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            amqpMessage.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
            return amqpMessage;
        });

        log.debug("Published posture evaluation message payloadId={} tenantId={} eventId={}",
                message.getPayloadId(),
                message.getTenantId(),
                message.getEventId());
    }

    private void validateSchema(PostureEvaluationMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Posture evaluation message is required");
        }
        Set<ConstraintViolation<PostureEvaluationMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid posture evaluation message schema: " + detail);
        }
        if (!Objects.equals(message.getSchemaVersion(), PostureEvaluationMessage.CURRENT_SCHEMA_VERSION)) {
            throw new IllegalArgumentException(
                    "Unsupported message schema version: " + message.getSchemaVersion()
            );
        }
    }
}
