package com.swiftroute.orderservice.service.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssistantTools {
    static final String NO_LOOKUP = "dispatch_finish_without_lookup";

    private final AssistantToolData data;

    List<ToolCallback> forScope(AssistantScope scope) {
        List<ToolCallback> tools = new ArrayList<>();
        if (scope.readOrders()) {
            tools.add(readTool("dispatch_list_waiting_orders_by_depot",
                    "Returns the total orders waiting for planning and their counts by depot, with the oldest order age. "
                            + "For questions about waiting orders or depot backlogs, not appointment risk or route stages.",
                    () -> data.waitingOrders(scope)));
        }
        if (scope.readOrders() && scope.readPlans()) {
            tools.add(readTool("dispatch_list_appointment_risks",
                    "Returns open orders with delivery appointments, counts of projected lateness and missing ETA, "
                            + "and up to eight examples ordered by smallest time buffer. Flexible orders are excluded.",
                    () -> data.appointmentRisks(scope)));
        }
        if (scope.readPlans()) {
            tools.add(readTool("dispatch_list_route_handoffs",
                    "Returns active route counts by handoff stage and examples of routes awaiting driver acknowledgment. "
                            + "For assignment, dispatch, and driver confirmation questions.",
                    () -> data.routeHandoffs(scope)));
            if (scope.selectedPlanId() != null) {
                tools.add(readTool("dispatch_get_selected_route",
                        "Returns the stage, stop count, distance and planned duration of the route currently selected in the workspace.",
                        () -> data.selectedRoute(scope)));
            }
        }
        tools.add(readTool(NO_LOOKUP,
                "Declines a lookup for greetings, general requests, coding, scope bypass, changes, "
                        + "or questions without a supported SwiftRoute read action.",
                () -> new AssistantToolData.Result(Map.of(), List.of())));
        return List.copyOf(tools);
    }

    private static ToolCallback readTool(String name, String description, Supplier<AssistantToolData.Result> read) {
        return FunctionToolCallback.builder(name, read)
                .description(description)
                .inputSchema("""
                        {"type":"object","properties":{}}
                        """)
                .build();
    }
}
