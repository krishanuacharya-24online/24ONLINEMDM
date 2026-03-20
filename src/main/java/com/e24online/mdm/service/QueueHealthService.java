package com.e24online.mdm.service;

import com.e24online.mdm.records.operations.QueueHealthEntryResponse;
import com.e24online.mdm.records.operations.QueueHealthSummaryResponse;
import com.e24online.mdm.web.dto.AuditQueueProperties;
import com.e24online.mdm.web.dto.PolicyAuditQueueProperties;
import com.e24online.mdm.web.dto.PostureQueueProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;

@Service
public class QueueHealthService {

    private final AmqpAdmin amqpAdmin;
    private final PostureQueueProperties postureQueueProperties;
    private final AuditQueueProperties auditQueueProperties;
    private final PolicyAuditQueueProperties policyAuditQueueProperties;

    public QueueHealthService(AmqpAdmin amqpAdmin,
                              PostureQueueProperties postureQueueProperties,
                              AuditQueueProperties auditQueueProperties,
                              PolicyAuditQueueProperties policyAuditQueueProperties) {
        this.amqpAdmin = amqpAdmin;
        this.postureQueueProperties = postureQueueProperties;
        this.auditQueueProperties = auditQueueProperties;
        this.policyAuditQueueProperties = policyAuditQueueProperties;
    }

    public Mono<QueueHealthSummaryResponse> getQueueHealthSummary() {
        return Mono.fromSupplier(this::buildQueueHealthSummary);
    }

    private QueueHealthSummaryResponse buildQueueHealthSummary() {
        List<QueueHealthEntryResponse> queues = List.of(
                snapshot(
                        "POSTURE_EVALUATION",
                        postureQueueProperties.getQueue(),
                        postureQueueProperties.getDeadLetterQueue(),
                        postureQueueProperties.getConsumerConcurrency(),
                        postureQueueProperties.getMaxConsumerConcurrency()
                ),
                snapshot(
                        "AUDIT_EVENT",
                        auditQueueProperties.getQueue(),
                        auditQueueProperties.getDeadLetterQueue(),
                        auditQueueProperties.getConsumerConcurrency(),
                        auditQueueProperties.getMaxConsumerConcurrency()
                ),
                snapshot(
                        "POLICY_AUDIT",
                        policyAuditQueueProperties.getQueue(),
                        policyAuditQueueProperties.getDeadLetterQueue(),
                        policyAuditQueueProperties.getConsumerConcurrency(),
                        policyAuditQueueProperties.getMaxConsumerConcurrency()
                )
        );

        long totalReadyMessages = queues.stream()
                .mapToLong(QueueHealthEntryResponse::readyMessages)
                .sum();
        long totalDeadLetterMessages = queues.stream()
                .mapToLong(QueueHealthEntryResponse::deadLetterMessages)
                .sum();
        String overallStatus = queues.stream().anyMatch(entry -> !"HEALTHY".equals(entry.status()))
                ? "DEGRADED"
                : "HEALTHY";

        return new QueueHealthSummaryResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                overallStatus,
                totalReadyMessages,
                totalDeadLetterMessages,
                queues
        );
    }

    private QueueHealthEntryResponse snapshot(String pipelineKey,
                                              String queueName,
                                              String deadLetterQueueName,
                                              int configuredConsumers,
                                              int maxConsumers) {
        try {
            Properties queueProperties = amqpAdmin.getQueueProperties(queueName);
            if (queueProperties == null) {
                return new QueueHealthEntryResponse(
                        pipelineKey,
                        queueName,
                        deadLetterQueueName,
                        0L,
                        0L,
                        0L,
                        configuredConsumers,
                        maxConsumers,
                        "UNAVAILABLE",
                        "Queue properties are unavailable"
                );
            }
            Properties deadLetterProperties = amqpAdmin.getQueueProperties(deadLetterQueueName);

            long readyMessages = longValue(queueProperties, String.valueOf(RabbitAdmin.QUEUE_MESSAGE_COUNT));
            long activeConsumers = longValue(queueProperties, String.valueOf(RabbitAdmin.QUEUE_CONSUMER_COUNT));
            long deadLetterMessages = deadLetterProperties == null
                    ? 0L
                    : longValue(deadLetterProperties, String.valueOf(RabbitAdmin.QUEUE_MESSAGE_COUNT));

            String status;
            if (deadLetterMessages > 0) {
                status = "DLQ_BACKLOG";
            } else if (readyMessages > 0 && activeConsumers == 0) {
                status = "CONSUMER_GAP";
            } else if (readyMessages > 0) {
                status = "BACKLOG";
            } else {
                status = "HEALTHY";
            }

            return new QueueHealthEntryResponse(
                    pipelineKey,
                    queueName,
                    deadLetterQueueName,
                    readyMessages,
                    deadLetterMessages,
                    activeConsumers,
                    configuredConsumers,
                    maxConsumers,
                    status,
                    null
            );
        } catch (RuntimeException ex) {
            return new QueueHealthEntryResponse(
                    pipelineKey,
                    queueName,
                    deadLetterQueueName,
                    0L,
                    0L,
                    0L,
                    configuredConsumers,
                    maxConsumers,
                    "UNAVAILABLE",
                    ex.getMessage()
            );
        }
    }

    private long longValue(Properties properties, String key) {
        if (properties == null || key == null) {
            return 0L;
        }
        Object value = properties.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return 0L;
    }
}
