package com.e24online.mdm.messaging;

import com.e24online.mdm.domain.AuditEventLog;
import com.e24online.mdm.repository.AuditEventLogRepository;
import com.e24online.mdm.service.messaging.AuditEventConsumer;
import com.e24online.mdm.web.dto.AuditEventMessage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditEventLogRepository auditEventLogRepository;

    private AuditEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        consumer = new AuditEventConsumer(auditEventLogRepository, validator, objectMapper);
    }

    @Test
    void consume_validMessage_persistsAuditRecord() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.now();
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
        message.setCreatedAt(createdAt);

        consumer.consume(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<AuditEventLog> captor = ArgumentCaptor.forClass(AuditEventLog.class);
        verify(auditEventLogRepository).save(captor.capture());
        AuditEventLog saved = captor.getValue();
        assertEquals("evt-1", saved.getEventId());
        assertEquals("AUTH", saved.getEventCategory());
        assertEquals("USER_LOGIN", saved.getEventType());
        assertEquals("LOGIN", saved.getAction());
        assertEquals("tenant-a", saved.getTenantId());
        assertEquals("alice", saved.getActor());
        assertEquals("AUTH_USER", saved.getEntityType());
        assertEquals("11", saved.getEntityId());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("{\"username\":\"alice\"}", saved.getMetadataJson());
        assertEquals(createdAt.toInstant(), saved.getCreatedAt().toInstant());
    }

    @Test
    void consume_invalidMessage_rejectedToDlq() {
        String invalidJson = "{\"schema_version\":999}";

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.consume(invalidJson.getBytes(StandardCharsets.UTF_8)));

        verify(auditEventLogRepository, never()).save(any(AuditEventLog.class));
    }
}

