package com.swiftroute.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** RabbitMQ and scheduler configuration for the durable tenant-event outbox and Dead-Letter Queue. */
@Configuration
@EnableScheduling
public class TenantEventConfig {
    public static final String EVENT_EXCHANGE = "swiftroute.events";
    public static final String DLX_EXCHANGE = "swiftroute.events.dlx";
    public static final String DLQ_QUEUE = "swiftroute.events.dlq";

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
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
