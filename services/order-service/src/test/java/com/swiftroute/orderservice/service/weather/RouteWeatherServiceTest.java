package com.swiftroute.orderservice.service.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import org.junit.jupiter.api.Test;

class RouteWeatherServiceTest {
    private final RainForecastClient forecasts = mock(RainForecastClient.class);
    private final RouteWeatherService service = new RouteWeatherService(mock(WeatherPlanData.class), forecasts);
    private final Instant now = Instant.parse("2026-11-19T05:00:00Z"); // noon Vietnam
    private final UUID plan = UUID.randomUUID();
    private final RainForecastClient.Point point = RainForecastClient.Point.of(10, 105);

    private WeatherPlanData.Stop stop(Instant eta, Instant deadline) {
        return new WeatherPlanData.Stop(plan, UUID.randomUUID(), "ORDER-1", 10.0, 105.0, eta, deadline);
    }

    @Test
    void evaluatesAtSavedArrivalAndFlagsRainWithTightOrMissedAppointment() {
        var eta = now.plusSeconds(600);
        when(forecasts.forecasts(anyList(), eq(now))).thenReturn(Map.of(point,
                new RainForecastClient.Forecast(now, Map.of(Instant.parse("2026-11-19T06:00:00Z").getEpochSecond(), 70))));
        var report = service.evaluate(List.of(stop(eta, eta.plusSeconds(900)), stop(eta, eta.minusSeconds(60)), stop(eta, null)), "today_afternoon", now);
        assertThat(report.evaluatedStops()).isEqualTo(3);
        assertThat(report.routes()).hasSize(1);
        assertThat(report.routes().getFirst().urgentStops()).isEqualTo(2);
        assertThat(report.routes().getFirst().stops()).extracting(RouteWeatherService.AffectedStop::bufferMinutes)
                .containsExactly(15L, -1L, null);
    }

    @Test
    void missingExpiredForecastPastEtaAndMissingEtaStayUnknown() {
        when(forecasts.forecasts(anyList(), eq(now))).thenReturn(Map.of(point,
                new RainForecastClient.Forecast(now.minusSeconds(601), Map.of())));
        var report = service.evaluate(List.of(stop(null, null), stop(now.minusSeconds(1), null), stop(now.plusSeconds(600), null)), "today", now);
        assertThat(report.unknownStops()).isEqualTo(3);
        assertThat(report.evaluatedStops()).isZero();
        assertThat(report.routes()).isEmpty();
    }

    @Test
    void dryAndOutOfPeriodStopsAreNotRainRisks() {
        var eta = now.plusSeconds(600);
        when(forecasts.forecasts(anyList(), eq(now))).thenReturn(Map.of(point,
                new RainForecastClient.Forecast(now, Map.of(Instant.parse("2026-11-19T06:00:00Z").getEpochSecond(), 9))));
        var report = service.evaluate(List.of(stop(eta, null), stop(now.plusSeconds(7 * 3600), null)), "today_afternoon", now);
        assertThat(report.evaluatedStops()).isEqualTo(1);
        assertThat(report.outsidePeriodStops()).isEqualTo(1);
        assertThat(report.routes()).isEmpty();
    }

    @Test
    void afternoonUsesVietnamDateAndTwelveToEighteen() {
        var interval = RouteWeatherService.interval("today_afternoon", Instant.parse("2026-11-19T01:00:00Z"));
        assertThat(interval).containsExactly(Instant.parse("2026-11-19T05:00:00Z"), Instant.parse("2026-11-19T11:00:00Z"));
    }

    @Test
    void parsesMissingProbabilityAsUnknownAndRejectsMismatchedPayload() throws Exception {
        var json = new ObjectMapper();
        var body = json.readTree("""
                {"hourly":{"time":[0,3600,7200,10800],"precipitation_probability":[null,50,-1,101]}}
                """);
        var parsed = RainForecastClient.parse(List.of(point), body, now);
        assertThat(parsed.get(point).hours()).containsExactlyEntriesOf(Map.of(3600L, 50));
        assertThat(RainForecastClient.parse(List.of(point, new RainForecastClient.Point(11, 106)), body, now)).isEmpty();
    }

    @Test
    void foreignTenantRouteIsNotLoaded() {
        var repository = mock(DispatchPlanRepository.class);
        UUID tenant = UUID.randomUUID();
        when(repository.findByIdAndTenantId(plan, tenant)).thenReturn(java.util.Optional.empty());
        assertThat(new WeatherPlanData(repository, new ObjectMapper()).load(tenant, plan)).isEmpty();
        verify(repository).findByIdAndTenantId(plan, tenant);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void snapshotIncludesServiceAdjustedEtaAndExcludesCompletedOrders() {
        var repository = mock(DispatchPlanRepository.class);
        UUID tenant = UUID.randomUUID(), id = UUID.randomUUID();
        var route = DispatchPlan.builder().id(plan).createdAt(now).build();
        route.addOrder(Order.builder().id(id).status(OrderStatus.PLANNED).deliveryLat(10.0).deliveryLng(105.0).build(), 1);
        route.addOrder(Order.builder().id(UUID.randomUUID()).status(OrderStatus.DELIVERED).build(), 2);
        route.setRouteWaypoints("[{\"stop_id\":\"" + id + "\",\"estimated_arrival_minutes\":17}]");
        when(repository.findByIdAndTenantId(plan, tenant)).thenReturn(java.util.Optional.of(route));
        var result = new WeatherPlanData(repository, new ObjectMapper()).load(tenant, plan);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().eta()).isEqualTo(now.plusSeconds(17 * 60));
    }

    @Test
    void dispatchedPlanUsesDispatchedAtInsteadOfUpdatedAtForEta() {
        var repository = mock(DispatchPlanRepository.class);
        UUID tenant = UUID.randomUUID(), id = UUID.randomUUID();
        var route = DispatchPlan.builder()
                .id(plan)
                .planStatus(PlanStatus.DISPATCHED)
                .createdAt(now.minusSeconds(600))
                .dispatchedAt(now)
                .updatedAt(now.plusSeconds(900))
                .build();
        route.addOrder(Order.builder().id(id).status(OrderStatus.PLANNED).deliveryLat(10.0).deliveryLng(105.0).build(), 1);
        route.setRouteWaypoints("[{\"stop_id\":\"" + id + "\",\"estimated_arrival_minutes\":17}]");
        when(repository.findByIdAndTenantId(plan, tenant)).thenReturn(java.util.Optional.of(route));
        var result = new WeatherPlanData(repository, new ObjectMapper()).load(tenant, plan);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().eta()).isEqualTo(now.plusSeconds(17 * 60));
    }
}
