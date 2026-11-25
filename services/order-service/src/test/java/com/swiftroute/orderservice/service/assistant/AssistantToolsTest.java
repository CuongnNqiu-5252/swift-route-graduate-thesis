package com.swiftroute.orderservice.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

class AssistantToolsTest {
    private final AssistantToolData data = mock(AssistantToolData.class);
    private final AssistantTools tools = new AssistantTools(data);
    private final AssistantScope scope = new AssistantScope(UUID.randomUUID(), UUID.randomUUID(), "OPERATOR", true, true, null);

    @Test
    void omitsWeatherAndStopDetailsWithoutOrderPermission() {
        var names = tools.forScope(new AssistantScope(scope.tenantId(), scope.userId(), "OPERATOR", false, true, null))
                .stream().map(tool -> tool.getToolDefinition().name()).toList();
        assertThat(names).doesNotContain("orders_search", "dispatch_get_weather_risks", "dispatch_get_route");
    }

    @Test
    void weatherToolCarriesServerScopeAndVietnamPeriod() {
        when(data.weatherRisks(scope, null, "today_afternoon")).thenReturn(new AssistantToolData.Result(
                Map.of("count", 1, "asOf", java.time.Instant.parse("2026-11-19T05:00:00Z")), List.of()));
        var tool = tools.forScope(scope).stream().filter(item -> item.getToolDefinition().name().equals("dispatch_get_weather_risks")).findFirst().orElseThrow();
        assertThat(tool.call("{\"period\":\"today_afternoon\"}")).contains("count");
        verify(data).weatherRisks(scope, null, "today_afternoon");
    }

    @Test
    void invalidPagingDoesNotQueryDatabase() {
        var tool = tools.forScope(scope).stream().filter(item -> item.getToolDefinition().name().equals("orders_search")).findFirst().orElseThrow();
        assertThat(tool.call("{\"limit\":10000}")).contains("invalid_arguments");
        verifyNoInteractions(data);
    }

    @Test
    void routeReferenceUsesFrontendContractEvenWithSnakeCaseMapper() throws Exception {
        var json = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        UUID id = UUID.randomUUID();
        var encoded = json.writeValueAsString(new AssistantToolData.Reference(id, "Route"));
        assertThat(json.readTree(encoded).path("planId").asText()).isEqualTo(id.toString());
        assertThat(json.readValue(encoded, AssistantToolData.Reference.class).planId()).isEqualTo(id);
    }
}
