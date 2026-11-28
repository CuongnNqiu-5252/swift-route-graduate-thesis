package com.swiftroute.orderservice.service.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import reactor.core.publisher.Flux;

class OperationsAssistantTest {
    private final GeminiAssistantModel provider = mock(GeminiAssistantModel.class);
    private final ChatModel model = mock(ChatModel.class);
    private final AssistantToolData data = mock(AssistantToolData.class);
    private final AssistantScope scope = new AssistantScope(UUID.randomUUID(), UUID.randomUUID(), "OPERATOR", true, true, null);

    private OperationsAssistant assistant() {
        when(provider.model()).thenReturn(model);
        when(provider.toolSelectionOptions(anyList())).thenAnswer(invocation -> GoogleGenAiChatOptions.builder()
                .toolCallbacks(invocation.<List<org.springframework.ai.tool.ToolCallback>>getArgument(0)).build());
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(text("Grounded answer")));
        return new OperationsAssistant(provider, new AssistantTools(data), new ObjectMapper());
    }

    private static ChatResponse text(String value) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(value))));
    }

    private static ChatResponse call(String id, String name, String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments))).build())));
    }

    @Test
    void chainsRouteDiscoveryThenDetailsAndPreservesToolEvidence() {
        var assistant = assistant();
        UUID plan = UUID.randomUUID();
        when(data.listRoutes(scope, 0, 10)).thenReturn(new AssistantToolData.Result(Map.of("planId", plan), List.of(new AssistantToolData.Reference(plan, "Route"))));
        when(data.routeDetails(scope, plan)).thenReturn(new AssistantToolData.Result(Map.of("stopCount", 3), List.of()));
        when(model.call(any(Prompt.class))).thenReturn(call("1", "dispatch_list_routes", "{}"),
                call("2", "dispatch_get_route", "{\"planId\":\"" + plan + "\"}"), text("ready"));
        var events = assistant.stream(List.of(), "Inspect the newest route", false, scope).collectList().block();
        assertThat(events).extracting(event -> event.event()).contains("context", "sources", "delta", "done");
        verify(data).routeDetails(scope, plan);
        var answer = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(answer.capture());
        assertThat(answer.getValue().getInstructions().stream().filter(ToolResponseMessage.class::isInstance).count()).isEqualTo(2);
    }

    @Test
    void repeatedLookupStopsWithoutExecutingTwice() {
        var assistant = assistant();
        when(data.listRoutes(scope, 0, 10)).thenReturn(new AssistantToolData.Result(Map.of("total", 0), List.of()));
        when(model.call(any(Prompt.class))).thenReturn(call("1", "dispatch_list_routes", "{\"limit\":10,\"page\":0}"),
                call("2", "dispatch_list_routes", "{\"page\":0,\"limit\":10}"));
        assistant.stream(List.of(), "List routes", false, scope).collectList().block();
        verify(data, times(1)).listRoutes(scope, 0, 10);
    }

    @Test
    void rejectsToolOutsidePermissionScope() {
        var assistant = assistant();
        when(model.call(any(Prompt.class))).thenReturn(call("1", "orders_search", "{}"));
        var limited = new AssistantScope(scope.tenantId(), scope.userId(), "OPERATOR", false, true, null);
        var events = assistant.stream(List.of(), "Orders", false, limited).collectList().block();
        assertThat(events).extracting(event -> event.event()).contains("error").doesNotContain("delta");
        verifyNoInteractions(data);
    }

    @Test
    void capsSuccessiveLookupsAtFourRounds() {
        var assistant = assistant();
        when(data.listRoutes(eq(scope), anyInt(), eq(10))).thenReturn(new AssistantToolData.Result(Map.of("hasMore", true), List.of()));
        when(model.call(any(Prompt.class))).thenReturn(call("1", "dispatch_list_routes", "{\"page\":0}"),
                call("2", "dispatch_list_routes", "{\"page\":1}"), call("3", "dispatch_list_routes", "{\"page\":2}"),
                call("4", "dispatch_list_routes", "{\"page\":3}"), call("5", "dispatch_list_routes", "{\"page\":4}"));
        assistant.stream(List.of(), "Read routes", false, scope).collectList().block();
        verify(model, times(4)).call(any(Prompt.class));
        verify(data, never()).listRoutes(scope, 4, 10);
    }

    @Test
    void rejectsInjectedTenantAndIncorrectParameterTypes() {
        var assistant = assistant();
        when(model.call(any(Prompt.class))).thenReturn(call("1", "orders_search", "{\"tenantId\":\"another-tenant\"}"),
                call("2", "orders_search", "{\"page\":1.5}"));
        for (int i = 0; i < 2; i++) {
            var events = assistant.stream(List.of(), "Orders", false, scope).collectList().block();
            assertThat(events).extracting(event -> event.event()).contains("error");
        }
        verifyNoInteractions(data);
    }
}
