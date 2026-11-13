package com.swiftroute.orderservice.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationTopologyConfig {
    @Bean
    public Declarables notificationTopology() {
        var exchange = new TopicExchange("swiftroute.events", true, false);
        var queue = new Queue("notification.tenant-workflow", true);
        return new Declarables(queue, BindingBuilder.bind(queue).to(exchange).with("tenant.#"),
                BindingBuilder.bind(queue).to(exchange).with("user.email_verification.v1"));
    }
}
