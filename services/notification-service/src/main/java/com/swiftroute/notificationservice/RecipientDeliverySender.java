package com.swiftroute.notificationservice;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

interface RecipientDeliverySender {
    void send(UUID eventId, String phone, String message);
}

@Component
@Slf4j
class SimulatedRecipientDeliverySender implements RecipientDeliverySender {
    @Override
    public void send(UUID eventId, String phone, String message) {
        log.info("Simulated recipient delivery notification for event {}", eventId);
    }
}
