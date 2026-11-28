package com.swiftroute.orderservice.service.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class OrderCreationGuide {
    static final String WORKFLOW = "To create an order, use Orders > Add orders > Create order. "
            + "Select a pickup location, enter the recipient, phone, and weight, and select the delivery point on the map. "
            + "The appointment window and COD are optional. Use Import CSV from Add orders for multiple orders.";

    private static final Pattern ORDER_CREATION = Pattern.compile(
            "\\b(?:(?:tao|them|lap) (?:mot )?(?:don|van don)|(?:create|add|place) (?:(?:an?|new) )*orders?)\\b");
    private static final Pattern HELP_REQUEST = Pattern.compile(
            "\\b(?:lam sao|the nao|nhu nao|sao de|cach|huong dan|o dau|how(?: to| do| can| should)?|where)\\b");

    private OrderCreationGuide() {}

    static boolean matches(String question) {
        String normalized = Normalizer.normalize(question.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replaceAll("\\s+", " ");
        return ORDER_CREATION.matcher(normalized).find() && HELP_REQUEST.matcher(normalized).find();
    }

    static List<String> steps(boolean canCreate, boolean english) {
        if (!canCreate) {
            return List.of(english
                    ? "To create an order from the Orders page, ask your workspace admin for permission to view and create orders."
                    : "Để tạo đơn trên trang Đơn hàng, hãy nhờ quản trị viên doanh nghiệp cấp quyền xem và tạo đơn.");
        }
        return english
                ? List.of("1. Go to **Orders → Add orders → Create order**.\n",
                        "2. Select a pickup location. Enter the recipient, phone and total weight, then select the delivery point on the map.\n",
                        "3. Add a delivery window or COD if needed, then click **Create order**.\n\n",
                        "For multiple orders, choose **Add orders → Import CSV**.")
                : List.of("1. Vào **Đơn hàng → Thêm đơn → Tạo đơn**.\n",
                        "2. Chọn điểm lấy hàng, nhập người nhận, số điện thoại và tổng khối lượng. Chọn điểm giao trên bản đồ.\n",
                        "3. Thêm khung giờ giao hoặc COD nếu cần, rồi bấm **Tạo đơn**.\n\n",
                        "Nếu cần tạo nhiều đơn, chọn **Thêm đơn → Nhập CSV**.");
    }
}
