package com.swiftroute.driverservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ and scheduler configuration for driver-service events outbox and Dead-Letter Queue.
 */
@Configuration
@EnableScheduling
public class DriverEventConfig {

    public static final String EVENT_EXCHANGE = "swiftroute.events";
    public static final String DLX_EXCHANGE = "swiftroute.events.dlx";
    public static final String DLQ_QUEUE = "swiftroute.events.dlq";
    public static final String DELIVERY_COMPLETED_QUEUE = "driver.earnings.delivery-completed.v1";
    public static final String DELIVERY_COMPLETED_ROUTING_KEY = "order.delivery_completed.v1";

    @Bean
    public TopicExchange swiftrouteEventsExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange swiftrouteDlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue swiftrouteDlq() {
        return new Queue(DLQ_QUEUE, true);
    }

    @Bean
    public Binding dlqBinding(Queue swiftrouteDlq, TopicExchange swiftrouteDlxExchange) {
        return BindingBuilder.bind(swiftrouteDlq).to(swiftrouteDlxExchange).with("#");
    }

    @Bean
    public Queue deliveryCompletedQueue() {
        return QueueBuilder.durable(DELIVERY_COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Binding deliveryCompletedBinding(Queue deliveryCompletedQueue, TopicExchange swiftrouteEventsExchange) {
        return BindingBuilder.bind(deliveryCompletedQueue).to(swiftrouteEventsExchange).with(DELIVERY_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
