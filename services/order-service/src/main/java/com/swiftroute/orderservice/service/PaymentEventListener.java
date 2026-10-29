package com.swiftroute.orderservice.service;

import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.config.PaymentEventConfig;
import com.swiftroute.orderservice.dto.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component @RequiredArgsConstructor @Slf4j
public class PaymentEventListener {
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    @RabbitListener(queues = PaymentEventConfig.QUEUE)
    public void onPaymentCompleted(Message message) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(message.getBody(), PaymentCompletedEvent.class);
        orderService.markPaidFromPaymentEvent(event.orderId(), event.paymentId());
        log.info("Processed payment.completed for order {}", event.orderId());
    }
}
