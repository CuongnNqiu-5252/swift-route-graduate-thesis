package com.swiftroute.notificationservice;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TenantNotificationConfig {

    public static final String QUEUE = "notification.tenant-workflow";
    public static final String OPS_ALERT_QUEUE = "notification.operational-alerts";

    @Bean
    public Declarables tenantNotifications() {
        var exchange = new TopicExchange("swiftroute.events", true, false);
        var queue = new Queue(QUEUE, true);
        var opsQueue = new Queue(OPS_ALERT_QUEUE, true);
        return new Declarables(
                exchange,
                queue,
                opsQueue,
                BindingBuilder.bind(queue).to(exchange).with("tenant.#"),
                BindingBuilder.bind(queue).to(exchange).with("user.email_verification.v1"),
                BindingBuilder.bind(queue).to(exchange).with("user.password_reset_requested.v1"),
                BindingBuilder.bind(opsQueue).to(exchange).with("plan.#"),
                BindingBuilder.bind(opsQueue).to(exchange).with("route.#"),
                BindingBuilder.bind(opsQueue).to(exchange).with("assignment.#"),
                BindingBuilder.bind(opsQueue).to(exchange).with("delivery.#"),
                BindingBuilder.bind(opsQueue).to(exchange).with("order.delivery_completed.v1"),
                BindingBuilder.bind(opsQueue).to(exchange).with("incident.resolved"),
                BindingBuilder.bind(opsQueue).to(exchange).with("return.#"),
                BindingBuilder.bind(opsQueue).to(exchange).with("cod.#"),
                BindingBuilder.bind(opsQueue).to(exchange).with("settlement.#"),
                BindingBuilder.bind(opsQueue).to(exchange).with("driver.unavailable_recovery"),
                BindingBuilder.bind(opsQueue).to(exchange).with("driver.profile_reviewed"));
    }
}
