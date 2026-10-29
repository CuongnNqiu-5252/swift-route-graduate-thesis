package com.swiftroute.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class PaymentEventConfig {
    public static final String QUEUE = "order.payment.completed.queue";
    @Bean Queue paymentCompletedQueue() { return new Queue(QUEUE, true); }
    @Bean Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange swiftrouteEventsExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(swiftrouteEventsExchange).with("payment.completed");
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
