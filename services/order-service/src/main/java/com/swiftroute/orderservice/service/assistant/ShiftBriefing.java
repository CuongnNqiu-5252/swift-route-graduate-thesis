package com.swiftroute.orderservice.service.assistant;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

final class ShiftBriefing {
    private ShiftBriefing() {}

    static String render(JsonNode facts, boolean english, List<AssistantAction> actions) {
        var lines = new ArrayList<String>();
        lines.add(english ? "**Orders & backlog**" : "**Đơn hàng & Tồn đọng**");
        appendBacklog(lines, facts.path(AssistantTools.WAITING_ORDERS), english);
        lines.add("");
        lines.add(english ? "**Operational risks**" : "**Rủi ro vận hành**");
        appendRisks(lines, facts, english);
        lines.add("");
        lines.add(english ? "**Recommended actions**" : "**Khuyến nghị hành động**");
        appendRecommendations(lines, facts, actions, english);
        return String.join("\n", lines);
    }

    private static void appendBacklog(List<String> lines, JsonNode backlog, boolean english) {
        if (!backlog.has("totalWaiting")) {
            lines.add(english ? "• Waiting order data unavailable." : "• Chưa có dữ liệu đơn chờ.");
            return;
        }
        lines.add((english ? "• **%d** waiting orders." : "• **%d** đơn chờ điều phối.")
                .formatted(backlog.path("totalWaiting").asInt()));
        var depots = backlog.path("depotExamplesOldestFirst");
        for (var depot : depots) {
            String name = depot.path("depot_name").asText("unknown");
            if ("unknown".equals(name)) name = english ? "Unknown depot" : "Kho chưa xác định";
            name = name.replaceAll("[\\r\\n*]+", " ").strip();
            lines.add((english ? "• %s: **%d** waiting, oldest **%d min**."
                    : "• %s: **%d** đơn, đơn lâu nhất chờ **%d phút**.")
                    .formatted(name, depot.path("waiting").asInt(), depot.path("oldest_minutes").asLong()));
        }
        if (backlog.path("totalDepots").asInt() > depots.size()) {
            lines.add(english ? "• Showing the depots with the oldest orders." : "• Danh sách ưu tiên các kho có đơn chờ lâu nhất.");
        }
    }

    private static void appendRisks(List<String> lines, JsonNode facts, boolean english) {
        var appointments = facts.path(AssistantTools.APPOINTMENT_RISKS);
        if (appointments.has("forecastLate")) {
            lines.add((english ? "• Appointments: **%d** projected late, **%d** with up to 15 minutes to spare, **%d** without an ETA."
                    : "• Giờ hẹn: **%d** đơn dự kiến trễ, **%d** đơn còn tối đa 15 phút, **%d** đơn chưa có ETA.")
                    .formatted(appointments.path("forecastLate").asInt(), appointments.path("tightBuffer").asInt(),
                            appointments.path("withoutEta").asInt()));
        } else {
            lines.add(english ? "• Appointment risk data unavailable." : "• Chưa có dữ liệu rủi ro giờ hẹn.");
        }

        var forecast = facts.path(AssistantTools.WEATHER_RISKS).path("weather");
        if (forecast.has("affectedRoutes")) {
            lines.add((english ? "• Rain in the next 24 hours: **%d** routes affected, **%d** stops without a reliable forecast at the saved ETA."
                    : "• Mưa trong 24 giờ tới: **%d** tuyến có khả năng gặp mưa, **%d** điểm chưa đánh giá được theo ETA.")
                    .formatted(forecast.path("affectedRoutes").asInt(), forecast.path("unknownStops").asInt()));
        } else {
            lines.add(english ? "• Rain forecast unavailable." : "• Chưa có dự báo mưa.");
        }

        var stages = facts.path(AssistantTools.ROUTE_HANDOFFS).path("stageCounts");
        if (stages.isObject()) {
            lines.add((english ? "• Dispatch: **%d** assigned routes awaiting dispatch, **%d** dispatched."
                    : "• Điều phối: **%d** tuyến đã gán tài xế chờ giao, **%d** tuyến đã giao cho tài xế.")
                    .formatted(stages.path("WAITING_TO_DISPATCH").asInt(), stages.path("DISPATCHED").asInt()));
        } else {
            lines.add(english ? "• Route handoff data unavailable." : "• Chưa có dữ liệu bàn giao tuyến.");
        }
    }

    private static void appendRecommendations(List<String> lines, JsonNode facts,
            List<AssistantAction> actions, boolean english) {
        if (actions.stream().anyMatch(action -> action.type() == AssistantAction.Type.NAVIGATE)) {
            lines.add(english ? "• Open routes with a risk and check the next stop."
                    : "• Mở tuyến có rủi ro để kiểm tra điểm dừng tiếp theo.");
        }
        if (actions.stream().anyMatch(action -> action.type() == AssistantAction.Type.TRIGGER_PLANNING)) {
            lines.add(english ? "• Run planning for a depot with eligible orders, prioritizing the longest wait."
                    : "• Lập kế hoạch cho kho có đơn đủ điều kiện, ưu tiên kho có đơn chờ lâu.");
        } else if (facts.path(AssistantTools.WAITING_ORDERS).path("totalWaiting").asInt() > 0) {
            lines.add(english ? "• Review the oldest waiting orders with the dispatcher."
                    : "• Cùng điều phối viên rà soát các đơn chờ lâu.");
        }
        if (lines.getLast().startsWith("**")) {
            lines.add(english ? "• Check any missing data and monitor the next shift updates."
                    : "• Kiểm tra nguồn dữ liệu còn thiếu và theo dõi cập nhật tiếp theo.");
        }
    }
}
