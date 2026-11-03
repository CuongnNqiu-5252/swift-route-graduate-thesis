package com.swiftroute.orderservice.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
@Configuration
public class NotificationTopologyConfig {
    @Bean public Declarables notificationTopology() {
        var exchange=new TopicExchange("swiftroute.events",true,false);var queue=new Queue("notification.tenant-workflow",true);
        return new Declarables(queue,BindingBuilder.bind(queue).to(exchange).with("tenant.#"),BindingBuilder.bind(queue).to(exchange).with("user.email_verification.v1"));
    }
}
