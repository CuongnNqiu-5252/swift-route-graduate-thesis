package com.swiftroute.driverservice.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.swiftroute.driverservice.config.DriverEventConfig;
import com.swiftroute.driverservice.dto.event.DeliveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryCompletedEventListener {
    private final DriverEarningService earnings;

    @RabbitListener(queues = DriverEventConfig.DELIVERY_COMPLETED_QUEUE)
    public void onDeliveryCompleted(DeliveryCompletedEvent event) {
        var earning = earnings.recordCompletedDelivery(event);
        log.info("Recorded earning {} for completed order {}", earning.getId(), event.orderId());
    }
}
