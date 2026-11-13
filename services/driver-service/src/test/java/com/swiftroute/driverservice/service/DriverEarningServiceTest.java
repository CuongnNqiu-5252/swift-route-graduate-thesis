package com.swiftroute.driverservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.driverservice.dto.event.DeliveryCompletedEvent;
import com.swiftroute.driverservice.dto.request.UpdateEarningPolicyRequest;
import com.swiftroute.driverservice.entity.Driver;
import com.swiftroute.driverservice.entity.DriverEarning;
import com.swiftroute.driverservice.entity.EarningPolicy;
import com.swiftroute.driverservice.repository.DriverEarningRepository;
import com.swiftroute.driverservice.repository.DriverRepository;
import com.swiftroute.driverservice.repository.DriverSettlementRepository;
import com.swiftroute.driverservice.repository.EarningPolicyRepository;
import com.swiftroute.driverservice.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class DriverEarningServiceTest {

    @Mock private DriverEarningRepository earnings;
    @Mock private EarningPolicyRepository policies;
    @Mock private DriverSettlementRepository settlements;
    @Mock private DriverRepository drivers;
    @Mock private OutboxEventRepository outboxEvents;
    @Mock private GoongRoadDistanceService roadDistanceService;

    private DriverEarningService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID driverUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DriverEarningService(earnings, policies, settlements, drivers, outboxEvents, roadDistanceService);
    }

    private EarningPolicy policy() {
        return EarningPolicy.builder()
                .tenantId(tenantId)
                .baseAmount(new BigDecimal("12000"))
                .perKmAmount(new BigDecimal("3000"))
                .minimumAmount(new BigDecimal("15000"))
                .heavyWeightThresholdKg(new BigDecimal("5.00"))
                .heavyBonusAmount(new BigDecimal("2000"))
                .build();
    }

    private DriverEarning recordWithGoong(Optional<BigDecimal> goongDistance) {
        Driver driver = Driver.builder().id(UUID.randomUUID()).userId(driverUserId).tenantId(tenantId)
                .fullName("Tai xe A").build();
        when(earnings.findByOrderId(any())).thenReturn(Optional.empty());
        when(drivers.findByUserId(driverUserId)).thenReturn(Optional.of(driver));
        when(policies.findById(tenantId)).thenReturn(Optional.of(policy()));
        when(earnings.save(any(DriverEarning.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roadDistanceService.getBikeRoadDistanceKm(any(), any(), any(), any())).thenReturn(goongDistance);

        DeliveryCompletedEvent event = new DeliveryCompletedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), tenantId, driverUserId, 10.0, 106.0, 10.03, 106.0, 2.0,
                BigDecimal.ZERO, Instant.parse("2026-11-12T03:00:00Z"));
        return service.recordCompletedDelivery(event);
    }

    @Test
    void usesGoongBikeRoadDistanceWhenAvailable() {
        // Goong Direction API returns exact motorcycle road distance: 4.18 km
        DriverEarning earning = recordWithGoong(Optional.of(new BigDecimal("4.18")));

        assertEquals(new BigDecimal("4.18"), earning.getPayableDistanceKm());
        // 4.18 * 3000 = 12540
        assertEquals(new BigDecimal("12540"), earning.getDistanceAmount());
        // 12000 + 12540 = 24540 -> rounded to 25000 VND
        assertEquals(new BigDecimal("25000"), earning.getNetAmount());
        assertTrue(earning.getCalculationSnapshot().contains("\"distance_source\":\"GOONG_BIKE_ROAD_DISTANCE\""));
        assertTrue(earning.getCalculationSnapshot().contains("\"payable_distance_km\":4.18"));
    }

    @Test
    void fallsBackToGeodesicDistanceWhenGoongUnavailable() {
        // When Goong is unconfigured or unavailable, fallback to straight-line distance (3.34 km)
        DriverEarning earning = recordWithGoong(Optional.empty());

        assertEquals(new BigDecimal("3.34"), earning.getPayableDistanceKm());
        // 3.34 * 3000 = 10020
        assertEquals(new BigDecimal("10020"), earning.getDistanceAmount());
        // 12000 + 10020 = 22020 -> rounded to 22000 VND
        assertEquals(new BigDecimal("22000"), earning.getNetAmount());
        assertTrue(earning.getCalculationSnapshot().contains("\"distance_source\":\"GEODESIC_FALLBACK\""));
        assertTrue(earning.getCalculationSnapshot().contains("\"payable_distance_km\":3.34"));
    }

    @Test
    void updatePolicySavesBasicParametersCleanly() {
        EarningPolicy existing = policy();
        when(policies.findById(tenantId)).thenReturn(Optional.of(existing));
        when(policies.save(any(EarningPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updatePolicy(tenantId, new UpdateEarningPolicyRequest(new BigDecimal("14000"),
                new BigDecimal("3500"), new BigDecimal("18000"), new BigDecimal("10.00"), new BigDecimal("5000")));

        assertEquals(new BigDecimal("14000"), response.baseAmount());
        assertEquals(new BigDecimal("3500"), response.perKmAmount());
        assertEquals(new BigDecimal("18000"), response.minimumAmount());
        assertEquals(new BigDecimal("10.00"), response.heavyWeightThresholdKg());
        assertEquals(new BigDecimal("5000"), response.heavyBonusAmount());
    }
}
