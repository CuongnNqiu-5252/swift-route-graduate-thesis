package com.swiftroute.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.swiftroute.orderservice.repository.TenantRepository;
import com.swiftroute.orderservice.service.RecipientDeliveryStartLookup;
import com.swiftroute.orderservice.service.RecipientDeliveryStartLookup.Details;

class InternalTenantDetailsControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final RecipientDeliveryStartLookup lookup = mock(RecipientDeliveryStartLookup.class);
    private final InternalTenantDetailsController controller =
            new InternalTenantDetailsController(mock(TenantRepository.class), lookup);

    @Test
    void rejectsMissingConfigurationAndIncorrectKeysBeforeLookingUpRecipientData() {
        ReflectionTestUtils.setField(controller, "internalKey", "");
        assertThatThrownBy(() -> controller.recipientDeliveryStart(tenantId, eventId, ""))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        ReflectionTestUtils.setField(controller, "internalKey", "test-service-key");
        assertThatThrownBy(() -> controller.recipientDeliveryStart(tenantId, eventId, "wrong"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(lookup);
    }

    @Test
    void returnsNoStoreDetailsOnlyForACurrentEventInTheRequestedTenant() {
        ReflectionTestUtils.setField(controller, "internalKey", "test-service-key");
        var details = new Details("0901234567", "TOKEN123", "SR-1042", "Kho Cần Thơ", null, null, "order.delivery_started.v1");
        when(lookup.findCurrent(tenantId, eventId)).thenReturn(Optional.of(details), Optional.empty());

        var response = controller.recipientDeliveryStart(tenantId, eventId, "test-service-key");
        assertThat(response.getBody()).isEqualTo(details);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");

        var skipped = controller.recipientDeliveryStart(tenantId, eventId, "test-service-key");
        assertThat(skipped.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(skipped.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
