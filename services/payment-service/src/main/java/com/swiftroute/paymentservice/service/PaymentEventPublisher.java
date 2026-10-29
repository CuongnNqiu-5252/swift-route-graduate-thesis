package com.swiftroute.paymentservice.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import com.swiftroute.paymentservice.config.RabbitMqConfig;
import com.swiftroute.paymentservice.dto.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class PaymentEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    public void publishCompleted(PaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EVENT_EXCHANGE, RabbitMqConfig.PAYMENT_COMPLETED_KEY, event);
    }
}
