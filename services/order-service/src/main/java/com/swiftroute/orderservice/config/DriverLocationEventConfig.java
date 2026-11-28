package com.swiftroute.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DriverLocationEventConfig {
    public static final String QUEUE = "order.driver-location.queue";
    public static final String ROUTING_KEY = "driver.location.updated.v1";

    @Bean
    public Queue driverLocationQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding driverLocationBinding(Queue driverLocationQueue, TopicExchange swiftrouteEventsExchange) {
        return BindingBuilder.bind(driverLocationQueue).to(swiftrouteEventsExchange).with(ROUTING_KEY);
    }
}
