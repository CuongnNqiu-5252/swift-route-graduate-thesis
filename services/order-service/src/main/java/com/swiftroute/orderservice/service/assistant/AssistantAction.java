package com.swiftroute.orderservice.service.assistant;

import java.util.Map;

public record AssistantAction(String id, Type type, String label, String variant, Map<String, ?> payload) {
    public enum Type { TRIGGER_PLANNING, NAVIGATE }

    static AssistantAction planning(AssistantToolData.PlanningDepot depot, boolean english) {
        String label = english ? "Plan routes for " + depot.depotName() : "Lập kế hoạch kho " + depot.depotName();
        return new AssistantAction("trigger_planning_" + depot.depotId(), Type.TRIGGER_PLANNING, label, "primary",
                Map.of("depot_id", depot.depotId(), "order_count", depot.orderCount()));
    }

    static AssistantAction navigate(AssistantToolData.Reference route, boolean english) {
        String label = (english ? "Open route " : "Mở tuyến ") + route.label();
        return new AssistantAction("navigate_" + route.planId(), Type.NAVIGATE, label, "secondary",
                Map.of("href", "/dispatch?plan=" + route.planId()));
    }

    static AssistantAction openOrders(boolean english) {
        return new AssistantAction("navigate_orders", Type.NAVIGATE, english ? "Open Orders" : "Mở trang Đơn hàng",
                "secondary", Map.of("href", "/orders"));
    }
}
