package com.e24online.mdm.messaging;

import com.e24online.mdm.service.messaging.AuditEventPublisher;
import com.e24online.mdm.web.dto.AuditEventMessage;
import com.e24online.mdm.web.dto.AuditQueueProperties;
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
class AuditEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AuditEventPublisher publisher;
    private AuditQueueProperties properties;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        properties = new AuditQueueProperties();
        publisher = new AuditEventPublisher(rabbitTemplate, properties, validator, new ObjectMapper());
    }

    @Test
    void publish_validMessage_sendsToConfiguredExchangeAndRoutingKey() {
        AuditEventMessage message = new AuditEventMessage();
        message.setSchemaVersion(AuditEventMessage.CURRENT_SCHEMA_VERSION);
        message.setEventId("evt-1");
        message.setEventCategory("AUTH");
        message.setEventType("USER_LOGIN");
        message.setAction("LOGIN");
        message.setTenantId("tenant-a");
        message.setActor("alice");
        message.setEntityType("AUTH_USER");
        message.setEntityId("11");
        message.setStatus("SUCCESS");
        message.setMetadataJson("{\"username\":\"alice\"}");
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
        AuditEventMessage invalid = new AuditEventMessage();
        invalid.setSchemaVersion(99);

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(invalid));
    }
}

