package com.swiftroute.authservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the Auth Service tenant lifecycle event queue and its topic binding. */
@Configuration
public class TenantEventConfig {
    public static final String EVENT_EXCHANGE = "swiftroute.events";
    public static final String TENANT_EVENT_QUEUE = "auth.tenant-events";

    @Bean
    public TopicExchange swiftrouteEventsExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue tenantEventQueue() {
        return new Queue(TENANT_EVENT_QUEUE, true);
    }

    @Bean
    public Binding tenantEventBinding(Queue tenantEventQueue, TopicExchange swiftrouteEventsExchange) {
        return BindingBuilder.bind(tenantEventQueue).to(swiftrouteEventsExchange).with("tenant.*");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
