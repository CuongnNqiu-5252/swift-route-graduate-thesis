package com.swiftroute.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for consuming order dispatch events from the Routing Engine.
 */
@Configuration
public class OrderDispatchEventConfig {

    public static final String QUEUE = "order.dispatch.completed.queue";
    public static final String ROUTING_KEY_SINGLE = "order.dispatched";
    public static final String ROUTING_KEY_BATCH = "order.batch.dispatched";

    @Bean
    public Queue orderDispatchCompletedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding orderDispatchSingleBinding(Queue orderDispatchCompletedQueue, TopicExchange swiftrouteEventsExchange) {
        return BindingBuilder.bind(orderDispatchCompletedQueue).to(swiftrouteEventsExchange).with(ROUTING_KEY_SINGLE);
    }

    @Bean
    public Binding orderDispatchBatchBinding(Queue orderDispatchCompletedQueue, TopicExchange swiftrouteEventsExchange) {
        return BindingBuilder.bind(orderDispatchCompletedQueue).to(swiftrouteEventsExchange).with(ROUTING_KEY_BATCH);
    }
}

