package com.swiftroute.orderservice.service.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssistantTools {
    static final String NO_LOOKUP = "dispatch_finish_without_lookup";
    static final String WORKSPACE_GUIDE = "workspace_get_capabilities";
    static final String WAITING_ORDERS = "dispatch_list_waiting_orders_by_depot";
    static final String APPOINTMENT_RISKS = "dispatch_list_appointment_risks";
    static final String WEATHER_RISKS = "dispatch_get_weather_risks";
    static final String ROUTE_HANDOFFS = "dispatch_list_route_handoffs";
    static final List<String> BRIEFING = List.of(WAITING_ORDERS, APPOINTMENT_RISKS, WEATHER_RISKS, ROUTE_HANDOFFS);

    private final AssistantToolData data;

    public record OrderLookup(String query, String status, Integer page, Integer limit) {}
    public record RoutePage(Integer page, Integer limit) {}
    public record RouteLookup(String planId) {}
    public record WeatherLookup(String planId, String period) {}
    private static final String PAGE_FIELDS = "\"page\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":100},"
            + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20}";
    private static String schema(String fields) {
        return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" + fields + "}}";
    }

    List<ToolCallback> forScope(AssistantScope scope) {
        List<ToolCallback> tools = new ArrayList<>();
        tools.add(readTool(WORKSPACE_GUIDE,
                "Explains SwiftRoute workflows, supported tools, permissions, limitations and current Vietnam time. "
                        + "Use for app help, current time, or when deciding what data can be checked.", () -> data.capabilities(scope)));
        if (scope.readOrders()) {
            tools.add(queryTool("orders_search", "Search tenant orders by order code, UUID, recipient, depot or address. "
                    + "Optional status, zero-based page and limit (1..20). Returns matching count and a page, not all records.",
                    OrderLookup.class, schema("\"query\":{\"type\":\"string\",\"maxLength\":200},"
                            + "\"status\":{\"type\":\"string\",\"enum\":[\"READY_FOR_DISPATCH\",\"PLANNED\",\"IN_TRANSIT\",\"DELIVERED\",\"FAILED\",\"RETURNING\",\"CANCELLED\",\"RETURNED\"]}," + PAGE_FIELDS),
                    args -> {
                        if (args.query() != null && args.query().length() > 200) throw new IllegalArgumentException("query too long");
                        return data.searchOrders(scope, args.query(), args.status(), page(args.page()), limit(args.limit()));
                    }));
            tools.add(readTool(WAITING_ORDERS,
                    "Returns the total orders waiting for planning and their counts by depot, with the oldest order age. "
                            + "For questions about waiting orders or depot backlogs, not appointment risk or route stages.",
                    () -> data.waitingOrders(scope)));
        }
        if (scope.readOrders() && scope.readPlans()) {
            tools.add(readTool(APPOINTMENT_RISKS,
                    "Returns open orders with delivery appointments, counts of projected lateness and missing ETA, "
                            + "and up to eight examples ordered by smallest time buffer. Flexible orders are excluded.",
                    () -> data.appointmentRisks(scope)));
        }
        if (scope.readPlans()) {
            tools.add(queryTool("dispatch_list_routes", "List tenant routes newest first, including stages and assignment. "
                    + "Use page and limit to paginate; use returned planId with dispatch_get_route for details.",
                    RoutePage.class, schema(PAGE_FIELDS),
                    args -> data.listRoutes(scope, page(args.page()), limit(args.limit()))));
            if (scope.readOrders()) {
                tools.add(queryTool("dispatch_get_route", "Get route and up to 50 stop details by full planId UUID. "
                        + "Omit planId only when a route is selected. Use dispatch_list_routes to resolve an unknown route ID.",
                        RouteLookup.class, schema("\"planId\":{\"type\":\"string\",\"format\":\"uuid\"}"),
                        args -> data.routeDetails(scope, planId(args.planId(), scope))));
                tools.add(queryTool(WEATHER_RISKS, "Find routes whose undelivered stops may encounter rain at saved ETA. "
                        + "period: today_afternoon (12:00..18:00 Vietnam today), today, tomorrow or next_24_hours (default). "
                        + "Optional planId restricts to one route; omit to scan all active tenant routes. Returns affected orders, "
                        + "rain probability, appointment buffer, combined urgency, and unknown coverage. Never converts rain to ETA delay.",
                        WeatherLookup.class, schema("\"planId\":{\"type\":\"string\",\"format\":\"uuid\"},"
                                + "\"period\":{\"type\":\"string\",\"enum\":[\"today_afternoon\",\"today\",\"tomorrow\",\"next_24_hours\"]}"),
                        args -> data.weatherRisks(scope, args.planId() == null ? null : UUID.fromString(args.planId()),
                                args.period() == null ? "next_24_hours" : args.period())));
            }
            tools.add(readTool(ROUTE_HANDOFFS,
                    "Returns active route counts by dispatch stage. "
                            + "For assignment and dispatch questions.",
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
                        {"type":"object","properties":{},"additionalProperties":false}
                        """)
                .build();
    }

    private static int page(Integer value) {
        int page = value == null ? 0 : value;
        if (page < 0 || page > 100) throw new IllegalArgumentException("page must be 0..100");
        return page;
    }

    private static int limit(Integer value) {
        int limit = value == null ? 10 : value;
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit must be 1..20");
        return limit;
    }

    private static UUID planId(String value, AssistantScope scope) {
        if (value != null) return UUID.fromString(value);
        if (scope.selectedPlanId() == null) throw new IllegalArgumentException("A full planId or selected route is required");
        return scope.selectedPlanId();
    }

    private static <I> ToolCallback queryTool(String name, String description, Class<I> input, String schema,
            Function<I, AssistantToolData.Result> read) {
        return FunctionToolCallback.builder(name, (Function<I, AssistantToolData.Result>) args -> {
            try {
                return read.apply(args);
            } catch (IllegalArgumentException invalid) {
                return new AssistantToolData.Result(Map.of("error", "invalid_arguments", "recovery",
                        "Use a valid UUID, supported enum and bounded page/limit, or ask the user to clarify."), List.of());
            }
        }).description(description).inputType(input).inputSchema(schema).build();
    }
}
