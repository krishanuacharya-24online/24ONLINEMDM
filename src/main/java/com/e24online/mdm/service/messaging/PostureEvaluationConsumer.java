package com.e24online.mdm.service.messaging;

import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.service.WorkflowOrchestrationService;
import com.e24online.mdm.web.dto.PostureEvaluationMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostureEvaluationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PostureEvaluationConsumer.class);

    private final WorkflowOrchestrationService workflowService;
    private final DevicePosturePayloadRepository payloadRepository;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public PostureEvaluationConsumer(WorkflowOrchestrationService workflowService,
                                     DevicePosturePayloadRepository payloadRepository,
                                     Validator validator,
                                     ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.payloadRepository = payloadRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(
            queues = "${mdm.posture.queue.queue}",
            containerFactory = "postureListenerContainerFactory"
    )
    public void consume(byte[] rawBody) {
        String rawMessage = rawBody == null ? null : new String(rawBody, StandardCharsets.UTF_8);
        PostureEvaluationMessage message = parseAndValidate(rawMessage);
        DevicePosturePayload payload = payloadRepository.findByIdAndTenant(message.getPayloadId(), message.getTenantId())
                .orElseThrow(() -> reject("Payload not found for queued message payloadId=" + message.getPayloadId()));

        if (!Objects.equals(payload.getIdempotencyKey(), message.getIdempotencyKey())) {
            throw reject("Idempotency key mismatch for payloadId=" + message.getPayloadId());
        }

        String status = normalize(payload.getProcessStatus());
        if ("EVALUATED".equals(status)) {
            log.debug("Skipping queued message for already evaluated payload payloadId={}", payload.getId());
            return;
        }

        log.debug("Consuming posture evaluation message payloadId={} tenantId={} eventId={}",
                message.getPayloadId(),
                message.getTenantId(),
                message.getEventId());
        workflowService.evaluateExistingPayload(message.getTenantId(), message.getPayloadId());
    }

    private PostureEvaluationMessage parseAndValidate(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw reject("Received empty posture evaluation message");
        }

        final PostureEvaluationMessage message;
        try {
            message = objectMapper.readValue(rawMessage, PostureEvaluationMessage.class);
        } catch (JacksonException _) {
            throw reject("Invalid posture evaluation message JSON");
        }

        Set<ConstraintViolation<PostureEvaluationMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw reject("Invalid posture evaluation message schema: " + detail);
        }

        if (!Objects.equals(message.getSchemaVersion(), PostureEvaluationMessage.CURRENT_SCHEMA_VERSION)) {
            throw reject("Unsupported posture message schema_version=" + message.getSchemaVersion());
        }

        return message;
    }

    private AmqpRejectAndDontRequeueException reject(String reason) {
        log.warn(reason);
        return new AmqpRejectAndDontRequeueException(reason);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
