package com.e24online.mdm.messaging;

import com.e24online.mdm.web.dto.PostureQueueProperties;
import com.e24online.mdm.service.messaging.PostureEvaluationPublisher;
import com.e24online.mdm.web.dto.PostureEvaluationMessage;
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
class PostureEvaluationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PostureEvaluationPublisher publisher;
    private PostureQueueProperties properties;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        properties = new PostureQueueProperties();
        publisher = new PostureEvaluationPublisher(rabbitTemplate, properties, validator, new ObjectMapper());
    }

    @Test
    void publish_validMessage_sendsToConfiguredExchangeAndRoutingKey() {
        PostureEvaluationMessage message = new PostureEvaluationMessage(
                PostureEvaluationMessage.CURRENT_SCHEMA_VERSION,
                "evt-1",
                "tenant-a",
                101L,
                "dev-1",
                "hash-1",
                "idempo-1",
                OffsetDateTime.now()
        );

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
        PostureEvaluationMessage invalid = new PostureEvaluationMessage();
        invalid.setSchemaVersion(99);

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(invalid));
    }
}
