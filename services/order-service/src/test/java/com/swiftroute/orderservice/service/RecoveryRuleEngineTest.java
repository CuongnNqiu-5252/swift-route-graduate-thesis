package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;
import com.swiftroute.orderservice.service.exception.RecoveryRuleEngine;

class RecoveryRuleEngineTest {
    @Test
    void operatorReportedIncidentTargetsAssignedDriverAndLinksToExactIncident() {
        UUID driver = UUID.randomUUID();
        UUID operator = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        var outbox = mock(OutboxEventRepository.class);
        var engine = new RecoveryRuleEngine(mock(DeliveryAttemptRepository.class),
                mock(ReturnRequestRepository.class), outbox);
        var incident = DeliveryIncident.builder().id(incidentId).tenantId(tenant)
                .driverId(driver).reportedBy(operator).incidentType("VEHICLE_BREAKDOWN").severity("HIGH").build();

        engine.evaluateAndApply(incident);

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        var event = captor.getValue();
        var payload = (Map<?, ?>) event.getPayload();
        assertThat(event.getEventType()).isEqualTo("delivery.incident");
        assertThat(payload.get("driver_id")).isEqualTo(driver.toString());
        assertThat(payload.get("reported_by")).isEqualTo(operator.toString());
        assertThat(payload.get("tenant_id")).isEqualTo(tenant.toString());
        assertThat(payload.get("action_url")).isEqualTo("/operations?incident=" + incidentId);
    }

    @Test
    void offlineSyncConflictAlwaysRequiresManualReviewWithoutRecoveryMutation() {
        var attempts = mock(DeliveryAttemptRepository.class);
        var returns = mock(ReturnRequestRepository.class);
        var outbox = mock(OutboxEventRepository.class);
        var engine = new RecoveryRuleEngine(attempts, returns, outbox);
        var order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.CANCELLED).build();
        var incident = DeliveryIncident.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .reportedBy(UUID.randomUUID())
                .order(order)
                .incidentType("OFFLINE_SYNC_CONFLICT")
                .severity("HIGH")
                .build();

        String result = engine.evaluateAndApply(incident);

        assertThat(result).isEqualTo("MANUAL_REVIEW");
        verifyNoInteractions(attempts, returns);
        verify(outbox).save(any(OutboxEvent.class));
    }
}
