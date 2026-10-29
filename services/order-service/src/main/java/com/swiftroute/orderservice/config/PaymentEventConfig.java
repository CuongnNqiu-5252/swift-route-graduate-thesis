package com.swiftroute.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {
    public static final String QUEUE = "order.payment.completed.queue";
    @Bean Queue paymentCompletedQueue() { return new Queue(QUEUE, true); }
    @Bean Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange swiftrouteEventsExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(swiftrouteEventsExchange).with("payment.completed");
    }
}
