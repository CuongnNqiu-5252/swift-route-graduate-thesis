package com.swiftroute.notificationservice;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration
@EnableScheduling
public class TenantNotificationConfig {
    public static final String QUEUE="notification.tenant-workflow";
    @Bean public Declarables tenantNotifications() {
        var exchange=new TopicExchange("swiftroute.events",true,false);
        var queue=new Queue(QUEUE,true);
        return new Declarables(exchange,queue,
            BindingBuilder.bind(queue).to(exchange).with("tenant.#"),
            BindingBuilder.bind(queue).to(exchange).with("user.email_verification.v1"));
    }
}
