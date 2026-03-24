package com.e24online.mdm.messaging;

import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.service.WorkflowOrchestrationService;
import com.e24online.mdm.service.messaging.PostureEvaluationConsumer;
import com.e24online.mdm.web.dto.PostureEvaluationMessage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostureEvaluationConsumerTest {

    @Mock
    private WorkflowOrchestrationService workflowService;

    @Mock
    private DevicePosturePayloadRepository payloadRepository;

    private PostureEvaluationConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        consumer = new PostureEvaluationConsumer(workflowService, payloadRepository, validator, objectMapper);
    }

    @Test
    void consume_validMessage_runsEvaluationForPayload() throws Exception {
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(100L);
        payload.setTenantId("tenant-a");
        payload.setIdempotencyKey("idempo-1");
        payload.setPayloadHash("hash-1");
        payload.setDeviceExternalId("dev-1");
        payload.setProcessStatus("QUEUED");

        PostureEvaluationMessage message = new PostureEvaluationMessage(
                PostureEvaluationMessage.CURRENT_SCHEMA_VERSION,
                "evt-1",
                "tenant-a",
                100L,
                "dev-1",
                "hash-1",
                "idempo-1",
                OffsetDateTime.now()
        );

        when(payloadRepository.findByIdAndTenant(100L, "tenant-a")).thenReturn(Optional.of(payload));

        consumer.consume(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8));

        verify(workflowService).evaluateExistingPayload("tenant-a", 100L);
    }

    @Test
    void consume_idempotencyMismatch_rejectedToDlqWithoutEvaluation() throws Exception {
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(100L);
        payload.setTenantId("tenant-a");
        payload.setIdempotencyKey("idempo-db");
        payload.setPayloadHash("hash-1");
        payload.setDeviceExternalId("dev-1");
        payload.setProcessStatus("QUEUED");

        PostureEvaluationMessage message = new PostureEvaluationMessage(
                PostureEvaluationMessage.CURRENT_SCHEMA_VERSION,
                "evt-1",
                "tenant-a",
                100L,
                "dev-1",
                "hash-1",
                "idempo-msg",
                OffsetDateTime.now()
        );

        when(payloadRepository.findByIdAndTenant(100L, "tenant-a")).thenReturn(Optional.of(payload));

        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumer.consume(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8)));

        verify(workflowService, never()).evaluateExistingPayload("tenant-a", 100L);
    }

    @Test
    void consume_payloadHashMismatch_rejectedToDlqWithoutEvaluation() throws Exception {
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(100L);
        payload.setTenantId("tenant-a");
        payload.setIdempotencyKey("idempo-1");
        payload.setPayloadHash("hash-db");
        payload.setDeviceExternalId("dev-1");
        payload.setProcessStatus("QUEUED");

        PostureEvaluationMessage message = new PostureEvaluationMessage(
                PostureEvaluationMessage.CURRENT_SCHEMA_VERSION,
                "evt-1",
                "tenant-a",
                100L,
                "dev-1",
                "hash-msg",
                "idempo-1",
                OffsetDateTime.now()
        );

        when(payloadRepository.findByIdAndTenant(100L, "tenant-a")).thenReturn(Optional.of(payload));

        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumer.consume(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8)));

        verify(workflowService, never()).evaluateExistingPayload("tenant-a", 100L);
    }

    @Test
    void consume_deviceExternalIdMismatch_rejectedToDlqWithoutEvaluation() throws Exception {
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(100L);
        payload.setTenantId("tenant-a");
        payload.setIdempotencyKey("idempo-1");
        payload.setPayloadHash("hash-1");
        payload.setDeviceExternalId("dev-db");
        payload.setProcessStatus("QUEUED");

        PostureEvaluationMessage message = new PostureEvaluationMessage(
                PostureEvaluationMessage.CURRENT_SCHEMA_VERSION,
                "evt-1",
                "tenant-a",
                100L,
                "dev-msg",
                "hash-1",
                "idempo-1",
                OffsetDateTime.now()
        );

        when(payloadRepository.findByIdAndTenant(100L, "tenant-a")).thenReturn(Optional.of(payload));

        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumer.consume(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8)));

        verify(workflowService, never()).evaluateExistingPayload("tenant-a", 100L);
    }
}
