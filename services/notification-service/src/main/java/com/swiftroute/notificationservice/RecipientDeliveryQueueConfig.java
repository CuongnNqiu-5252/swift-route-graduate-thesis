package com.swiftroute.notificationservice;

import java.time.Duration;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RecipientDeliveryQueueConfig {
    static final String DELIVERY_STARTED = "order.delivery_started.v1";
    static final String DELIVERY_FAILED = "order.recipient_delivery_failed.v1";
    static final String DELIVERY_COMPLETED = "order.recipient_delivery_completed.v1";
    static final String QUEUE = "notification.recipient-delivery-started";
    static final String FAILED_QUEUE = QUEUE + ".failed";
    static final String DEAD_LETTER_EXCHANGE = "swiftroute.recipient-delivery-started.dlx";

    @Bean
    Declarables recipientDeliveryTopology() {
        TopicExchange events = new TopicExchange("swiftroute.events", true, false);
        FanoutExchange deadLetters = new FanoutExchange(DEAD_LETTER_EXCHANGE);
        Queue delivery = QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
        Queue failed = QueueBuilder.durable(FAILED_QUEUE).build();
        return new Declarables(events, deadLetters, delivery, failed,
                BindingBuilder.bind(delivery).to(events).with(DELIVERY_STARTED),
                BindingBuilder.bind(delivery).to(events).with(DELIVERY_FAILED),
                BindingBuilder.bind(delivery).to(events).with(DELIVERY_COMPLETED),
                BindingBuilder.bind(failed).to(deadLetters));
    }

    @Bean
    SimpleRabbitListenerContainerFactory recipientDeliveryListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory, RecipientDeliveryOutcomes outcomes) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .configureRetryPolicy(policy -> policy.excludes(AmqpRejectAndDontRequeueException.class)
                        .maxRetries(2).delay(Duration.ofSeconds(1))
                        .multiplier(2))
                .recoverer((message, cause) -> {
                    String id = message.getMessageProperties().getMessageId();
                    if (id != null) {
                        try {
                            outcomes.record(UUID.fromString(id), RecipientDeliveryOutcomes.State.FAILED);
                        } catch (IllegalArgumentException | org.springframework.dao.DataAccessException ignored) {
                            // The dead letter remains available when the outcome cannot be recorded.
                        }
                    }
                    new RejectAndDontRequeueRecoverer().recover(message, cause);
                })
                .build());
        return factory;
    }
}
