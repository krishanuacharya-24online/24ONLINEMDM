package com.e24online.mdm.service;

import com.e24online.mdm.domain.AuditEventLog;
import com.e24online.mdm.repository.AuditEventLogRepository;
import com.e24online.mdm.service.messaging.AuditEventPublisher;
import com.e24online.mdm.web.dto.AuditEventMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    @Mock
    private AuditEventLogRepository auditEventLogRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private AuditEventService service;

    @BeforeEach
    void setUp() {
        service = new AuditEventService(
                auditEventLogRepository,
                auditEventPublisher,
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void record_publishSuccess_doesNotUseDirectSave() {
        service.record(
                "AUTH",
                "USER_LOGIN",
                "LOGIN",
                "tenant-a",
                "alice",
                "AUTH_USER",
                "11",
                "SUCCESS",
                java.util.Map.of("username", "alice")
        );

        verify(auditEventPublisher, times(1)).publish(any(AuditEventMessage.class));
        verify(auditEventLogRepository, never()).save(any(AuditEventLog.class));
    }

    @Test
    void record_publishFailure_fallsBackToDirectSave() {
        org.mockito.Mockito.doThrow(new RuntimeException("queue down"))
                .when(auditEventPublisher)
                .publish(any(AuditEventMessage.class));

        service.record(
                "POSTURE",
                "POSTURE_EVALUATED",
                "EVALUATE",
                "tenant-a",
                "rule-engine",
                "DEVICE_POSTURE_PAYLOAD",
                "15",
                "SUCCESS",
                java.util.Map.of("payloadId", 15L)
        );

        verify(auditEventPublisher, times(1)).publish(any(AuditEventMessage.class));
        verify(auditEventLogRepository, times(1)).save(any(AuditEventLog.class));
    }
}

