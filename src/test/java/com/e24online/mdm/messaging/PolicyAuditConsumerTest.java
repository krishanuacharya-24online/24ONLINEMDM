package com.e24online.mdm.messaging;

import com.e24online.mdm.domain.PolicyChangeAudit;
import com.e24online.mdm.repository.PolicyChangeAuditRepository;
import com.e24online.mdm.service.messaging.PolicyAuditConsumer;
import com.e24online.mdm.web.dto.PolicyAuditMessage;
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
class PolicyAuditConsumerTest {

    @Mock
    private PolicyChangeAuditRepository policyChangeAuditRepository;

    private PolicyAuditConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        consumer = new PolicyAuditConsumer(policyChangeAuditRepository, validator, objectMapper);
    }

    @Test
    void consume_validMessage_persistsAuditRecord() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.now();
        PolicyAuditMessage message = new PolicyAuditMessage();
        message.setSchemaVersion(PolicyAuditMessage.CURRENT_SCHEMA_VERSION);
        message.setEventId("evt-1");
        message.setPolicyType("SYSTEM_RULE");
        message.setPolicyId(100L);
        message.setOperation("UPDATE");
        message.setTenantId("tenant-a");
        message.setActor("auditor");
        message.setApprovalTicket("APP-7");
        message.setBeforeStateJson("{\"before\":true}");
        message.setAfterStateJson("{\"after\":true}");
        message.setCreatedAt(createdAt);

        consumer.consume(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<PolicyChangeAudit> captor = ArgumentCaptor.forClass(PolicyChangeAudit.class);
        verify(policyChangeAuditRepository).save(captor.capture());
        PolicyChangeAudit saved = captor.getValue();
        assertEquals("SYSTEM_RULE", saved.getPolicyType());
        assertEquals(100L, saved.getPolicyId());
        assertEquals("UPDATE", saved.getOperation());
        assertEquals("tenant-a", saved.getTenantId());
        assertEquals("auditor", saved.getActor());
        assertEquals("APP-7", saved.getApprovalTicket());
        assertEquals("{\"before\":true}", saved.getBeforeStateJson());
        assertEquals("{\"after\":true}", saved.getAfterStateJson());
        assertEquals(createdAt.toInstant(), saved.getCreatedAt().toInstant());
    }

    @Test
    void consume_invalidMessage_rejectedToDlq() {
        String invalidJson = "{\"schema_version\":999}";

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.consume(invalidJson.getBytes(StandardCharsets.UTF_8)));

        verify(policyChangeAuditRepository, never()).save(any(PolicyChangeAudit.class));
    }
}
