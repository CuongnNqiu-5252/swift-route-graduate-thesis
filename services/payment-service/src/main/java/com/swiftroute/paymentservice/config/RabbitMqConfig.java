package com.swiftroute.paymentservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String EVENT_EXCHANGE = "swiftroute.events";
    public static final String PAYMENT_COMPLETED_KEY = "payment.completed";
    @Bean TopicExchange swiftrouteEventsExchange() { return new TopicExchange(EVENT_EXCHANGE, true, false); }
    @Bean MessageConverter messageConverter() { return new Jackson2JsonMessageConverter(); }
}
