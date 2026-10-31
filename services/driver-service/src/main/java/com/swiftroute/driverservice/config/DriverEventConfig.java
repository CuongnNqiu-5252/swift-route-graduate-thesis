package com.swiftroute.driverservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ and scheduler configuration for driver-service events outbox.
 */
@Configuration
@EnableScheduling
public class DriverEventConfig {

    public static final String EVENT_EXCHANGE = "swiftroute.events";

    @Bean
    public TopicExchange swiftrouteEventsExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
