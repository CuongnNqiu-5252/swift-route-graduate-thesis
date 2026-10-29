package com.swiftroute.driverservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.driverservice.dto.request.ReportDeliveryEventRequest;
import com.swiftroute.driverservice.dto.request.UpdateAvailabilityRequest;
import com.swiftroute.driverservice.dto.request.UpdateLocationRequest;
import com.swiftroute.driverservice.dto.response.DriverResponse;
import com.swiftroute.driverservice.entity.Driver;
import com.swiftroute.driverservice.entity.OutboxEvent;
import com.swiftroute.driverservice.entity.enums.DriverStatus;
import com.swiftroute.driverservice.entity.enums.DriverType;
import com.swiftroute.driverservice.entity.enums.VehicleType;
import com.swiftroute.driverservice.exception.AppException;
import com.swiftroute.driverservice.repository.DriverRepository;
import com.swiftroute.driverservice.repository.OutboxEventRepository;
import com.swiftroute.driverservice.service.DriverService;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private DriverService driverService;

    private Driver sampleDriver;
    private UUID sampleDriverId;
    private UUID sampleUserId;
    private UUID sampleTenantId;

    @BeforeEach
    void setUp() {
        driverService = new DriverService(driverRepository, outboxEventRepository);

        sampleDriverId = UUID.randomUUID();
        sampleUserId = UUID.randomUUID();
        sampleTenantId = UUID.randomUUID();

        sampleDriver = Driver.builder()
                .id(sampleDriverId)
                .userId(sampleUserId)
                .tenantId(sampleTenantId)
                .fullName("Nguyen Van Tai Xe")
                .phone("0987654321")
                .licensePlate("59-X1 12345")
                .vehicleType(VehicleType.MOTORCYCLE)
                .capacity(50)
                .driverType(DriverType.DEDICATED)
                .status(DriverStatus.ACTIVE)
                .isAvailable(true)
                .currentLat(10.7769)
                .currentLng(106.7009)
                .build();
    }

    @Test
    @DisplayName("getMyProfile - returns driver when found by userId")
    void getMyProfile_Success() {
        when(driverRepository.findByUserId(sampleUserId)).thenReturn(Optional.of(sampleDriver));

        DriverResponse response = driverService.getMyProfile(sampleUserId);

        assertNotNull(response);
        assertEquals(sampleDriverId, response.id());
        assertEquals("Nguyen Van Tai Xe", response.fullName());
        assertEquals("0987654321", response.phone());
    }

    @Test
    @DisplayName("getMyProfile - throws DRIVER_NOT_FOUND when driver does not exist")
    void getMyProfile_NotFound() {
        UUID unknownUserId = UUID.randomUUID();
        when(driverRepository.findByUserId(unknownUserId)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> driverService.getMyProfile(unknownUserId));
    }

    @Test
    @DisplayName("updateMyAvailability - updates availability and creates outbox event")
    void updateMyAvailability_Success() {
        when(driverRepository.findByUserId(sampleUserId)).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAvailabilityRequest request = new UpdateAvailabilityRequest(false);
        DriverResponse response = driverService.updateMyAvailability(sampleUserId, request);

        assertFalse(response.isAvailable());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent event = outboxCaptor.getValue();
        assertEquals("driver.availability_changed.v1", event.getEventType());
        assertEquals("DRIVER", event.getAggregateType());
        assertEquals(sampleDriverId, event.getAggregateId());
    }

    @Test
    @DisplayName("updateMyLocation - updates latitude and longitude")
    void updateMyLocation_Success() {
        when(driverRepository.findByUserId(sampleUserId)).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLocationRequest request = new UpdateLocationRequest(10.8231, 106.6297);
        DriverResponse response = driverService.updateMyLocation(sampleUserId, request);

        assertEquals(10.8231, response.currentLat());
        assertEquals(106.6297, response.currentLng());
    }

    @Test
    @DisplayName("reportDeliveryEvent - creates driver.delivery_reported.v1 outbox event")
    void reportDeliveryEvent_Success() {
        when(driverRepository.findByUserId(sampleUserId)).thenReturn(Optional.of(sampleDriver));

        UUID orderId = UUID.randomUUID();
        ReportDeliveryEventRequest request = new ReportDeliveryEventRequest(orderId, "DELIVERED", null,
                "Delivered to customer");

        driverService.reportDeliveryEvent(sampleUserId, request);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent event = outboxCaptor.getValue();
        assertEquals("driver.delivery_reported.v1", event.getEventType());
        assertEquals(sampleDriverId, event.getAggregateId());
    }

    @Test
    @DisplayName("updateMyAvailability - throws DRIVER_SUSPENDED when driver is suspended")
    void updateMyAvailability_Suspended() {
        sampleDriver.setStatus(DriverStatus.SUSPENDED);
        when(driverRepository.findByUserId(sampleUserId)).thenReturn(Optional.of(sampleDriver));

        UpdateAvailabilityRequest request = new UpdateAvailabilityRequest(true);
        assertThrows(AppException.class, () -> driverService.updateMyAvailability(sampleUserId, request));
    }
}
