package com.e24online.mdm.config;

import com.e24online.mdm.web.dto.AuditQueueProperties;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(AuditQueueProperties.class)
public class AuditRabbitMqConfig {

    @Bean
    public DirectExchange auditEventExchange(AuditQueueProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange auditEventDeadLetterExchange(AuditQueueProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue auditEventQueue(AuditQueueProperties properties) {
        return QueueBuilder.durable(properties.getQueue())
                .withArgument("x-dead-letter-exchange", properties.getDeadLetterExchange())
                .withArgument("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey())
                .withArgument("x-message-ttl", properties.getMessageTtlMs())
                .build();
    }

    @Bean
    public Queue auditEventDeadLetterQueue(AuditQueueProperties properties) {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding auditEventBinding(@Qualifier("auditEventQueue") Queue auditEventQueue,
                                     @Qualifier("auditEventExchange") DirectExchange auditEventExchange,
                                     AuditQueueProperties properties) {
        return BindingBuilder.bind(auditEventQueue)
                .to(auditEventExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public Binding auditEventDeadLetterBinding(@Qualifier("auditEventDeadLetterQueue") Queue auditEventDeadLetterQueue,
                                               @Qualifier("auditEventDeadLetterExchange") DirectExchange auditEventDeadLetterExchange,
                                               AuditQueueProperties properties) {
        return BindingBuilder.bind(auditEventDeadLetterQueue)
                .to(auditEventDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    @Bean(name = "auditListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory auditListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                              AuditQueueProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(Math.max(1, properties.getConsumerConcurrency()));
        factory.setMaxConcurrentConsumers(Math.max(
                Math.max(1, properties.getConsumerConcurrency()),
                properties.getMaxConsumerConcurrency()
        ));

        Advice retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxRetries(properties.getRetryMaxAttempts() - 1)
                .backOffOptions(
                        properties.getRetryInitialIntervalMs(),
                        properties.getRetryMultiplier(),
                        properties.getRetryMaxIntervalMs()
                )
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();

        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}

