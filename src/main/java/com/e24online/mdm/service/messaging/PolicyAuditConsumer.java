package com.e24online.mdm.service.messaging;

import com.e24online.mdm.domain.PolicyChangeAudit;
import com.e24online.mdm.repository.PolicyChangeAuditRepository;
import com.e24online.mdm.web.dto.PolicyAuditMessage;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PolicyAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(PolicyAuditConsumer.class);

    private final PolicyChangeAuditRepository policyChangeAuditRepository;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public PolicyAuditConsumer(PolicyChangeAuditRepository policyChangeAuditRepository,
                               Validator validator,
                               ObjectMapper objectMapper) {
        this.policyChangeAuditRepository = policyChangeAuditRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(
            queues = "${mdm.policy.audit.queue.queue}",
            containerFactory = "policyAuditListenerContainerFactory"
    )
    public void consume(byte[] rawBody) {
        String rawMessage = rawBody == null ? null : new String(rawBody, StandardCharsets.UTF_8);
        PolicyAuditMessage message = parseAndValidate(rawMessage);

        PolicyChangeAudit audit = new PolicyChangeAudit();
        audit.setPolicyType(message.getPolicyType());
        audit.setPolicyId(message.getPolicyId());
        audit.setOperation(message.getOperation());
        audit.setTenantId(message.getTenantId());
        audit.setActor(message.getActor());
        audit.setApprovalTicket(message.getApprovalTicket());
        audit.setBeforeStateJson(message.getBeforeStateJson());
        audit.setAfterStateJson(message.getAfterStateJson());
        audit.setCreatedAt(message.getCreatedAt());

        policyChangeAuditRepository.save(audit);
        log.info("Persisted policy audit eventId={} policyType={} operation={} policyId={}",
                message.getEventId(),
                message.getPolicyType(),
                message.getOperation(),
                message.getPolicyId());
    }

    private PolicyAuditMessage parseAndValidate(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw reject("Received empty policy audit message");
        }

        final PolicyAuditMessage message;
        try {
            message = objectMapper.readValue(rawMessage, PolicyAuditMessage.class);
        } catch (JacksonException _) {
            throw reject("Invalid policy audit message JSON");
        }

        Set<ConstraintViolation<PolicyAuditMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw reject("Invalid policy audit message schema: " + detail);
        }

        if (!Objects.equals(message.getSchemaVersion(), PolicyAuditMessage.CURRENT_SCHEMA_VERSION)) {
            throw reject("Unsupported policy audit schema_version=" + message.getSchemaVersion());
        }

        return message;
    }

    private AmqpRejectAndDontRequeueException reject(String reason) {
        log.warn(reason);
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
