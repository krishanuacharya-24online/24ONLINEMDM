package com.e24online.mdm.messaging;

import com.e24online.mdm.service.messaging.PolicyAuditPublisher;
import com.e24online.mdm.web.dto.PolicyAuditMessage;
import com.e24online.mdm.web.dto.PolicyAuditQueueProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyAuditPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PolicyAuditPublisher publisher;
    private PolicyAuditQueueProperties properties;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        properties = new PolicyAuditQueueProperties();
        publisher = new PolicyAuditPublisher(rabbitTemplate, properties, validator, new ObjectMapper());
    }

    @Test
    void publish_validMessage_sendsToConfiguredExchangeAndRoutingKey() {
        PolicyAuditMessage message = new PolicyAuditMessage();
        message.setSchemaVersion(PolicyAuditMessage.CURRENT_SCHEMA_VERSION);
        message.setEventId("evt-1");
        message.setPolicyType("SYSTEM_RULE");
        message.setPolicyId(101L);
        message.setOperation("CREATE");
        message.setTenantId("tenant-a");
        message.setActor("admin");
        message.setCreatedAt(OffsetDateTime.now());

        publisher.publish(message);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(properties.getExchange()),
                eq(properties.getRoutingKey()),
                anyString(),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    void publish_invalidMessage_rejectedBeforeSend() {
        PolicyAuditMessage invalid = new PolicyAuditMessage();
        invalid.setSchemaVersion(99);

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(invalid));
    }
}
