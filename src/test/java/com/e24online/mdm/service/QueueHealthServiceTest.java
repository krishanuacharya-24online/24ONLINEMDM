package com.e24online.mdm.service;

import com.e24online.mdm.records.operations.QueueHealthSummaryResponse;
import com.e24online.mdm.web.dto.AuditQueueProperties;
import com.e24online.mdm.web.dto.PolicyAuditQueueProperties;
import com.e24online.mdm.web.dto.PostureQueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueHealthServiceTest {

    @Mock
    private AmqpAdmin amqpAdmin;

    private QueueHealthService service;

    @BeforeEach
    void setUp() {
        service = new QueueHealthService(
                amqpAdmin,
                postureProperties(),
                auditProperties(),
                policyAuditProperties()
        );
    }

    @Test
    void getQueueHealthSummary_mapsConfiguredQueues() {
        when(amqpAdmin.getQueueProperties("posture.queue")).thenReturn(queueProps(5, 2));
        when(amqpAdmin.getQueueProperties("posture.dlq")).thenReturn(queueProps(1, 0));
        when(amqpAdmin.getQueueProperties("audit.queue")).thenReturn(queueProps(0, 1));
        when(amqpAdmin.getQueueProperties("audit.dlq")).thenReturn(queueProps(0, 0));
        when(amqpAdmin.getQueueProperties("policy.queue")).thenReturn(queueProps(0, 1));
        when(amqpAdmin.getQueueProperties("policy.dlq")).thenReturn(queueProps(0, 0));

        QueueHealthSummaryResponse response = service.getQueueHealthSummary().block();

        assertNotNull(response);
        assertEquals("DEGRADED", response.overallStatus());
        assertEquals(5L, response.totalReadyMessages());
        assertEquals(1L, response.totalDeadLetterMessages());
        assertEquals(3, response.queues().size());
        assertEquals("POSTURE_EVALUATION", response.queues().getFirst().pipelineKey());
        assertEquals("DLQ_BACKLOG", response.queues().getFirst().status());
        assertEquals(5L, response.queues().getFirst().readyMessages());
        assertEquals(1L, response.queues().getFirst().deadLetterMessages());
    }

    @Test
    void getQueueHealthSummary_marksUnavailableQueues() {
        when(amqpAdmin.getQueueProperties("posture.queue")).thenReturn(null);
        when(amqpAdmin.getQueueProperties("audit.queue")).thenReturn(queueProps(0, 1));
        when(amqpAdmin.getQueueProperties("audit.dlq")).thenReturn(queueProps(0, 0));
        when(amqpAdmin.getQueueProperties("policy.queue")).thenReturn(queueProps(0, 1));
        when(amqpAdmin.getQueueProperties("policy.dlq")).thenReturn(queueProps(0, 0));

        QueueHealthSummaryResponse response = service.getQueueHealthSummary().block();

        assertNotNull(response);
        assertEquals("DEGRADED", response.overallStatus());
        assertEquals("UNAVAILABLE", response.queues().getFirst().status());
        assertEquals("Queue properties are unavailable", response.queues().getFirst().errorMessage());
    }

    private PostureQueueProperties postureProperties() {
        PostureQueueProperties properties = new PostureQueueProperties();
        properties.setQueue("posture.queue");
        properties.setDeadLetterQueue("posture.dlq");
        properties.setConsumerConcurrency(2);
        properties.setMaxConsumerConcurrency(6);
        return properties;
    }

    private AuditQueueProperties auditProperties() {
        AuditQueueProperties properties = new AuditQueueProperties();
        properties.setQueue("audit.queue");
        properties.setDeadLetterQueue("audit.dlq");
        properties.setConsumerConcurrency(1);
        properties.setMaxConsumerConcurrency(4);
        return properties;
    }

    private PolicyAuditQueueProperties policyAuditProperties() {
        PolicyAuditQueueProperties properties = new PolicyAuditQueueProperties();
        properties.setQueue("policy.queue");
        properties.setDeadLetterQueue("policy.dlq");
        properties.setConsumerConcurrency(1);
        properties.setMaxConsumerConcurrency(4);
        return properties;
    }

    private Properties queueProps(int messageCount, int consumerCount) {
        Properties properties = new Properties();
        properties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, messageCount);
        properties.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, consumerCount);
        return properties;
    }
}
