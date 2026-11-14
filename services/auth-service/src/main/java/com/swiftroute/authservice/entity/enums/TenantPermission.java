package com.swiftroute.authservice.entity.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canonical catalog of granular tenant-level permissions.
 * System-level roles remain strictly USER and SYSTEM_ADMIN; these permissions
 * govern capabilities within an enterprise tenant (OWNER, TENANT_ADMIN, OPERATOR, DRIVER, VIEWER).
 */
public enum TenantPermission {

    // Orders
    ORDER_READ("order:read", "orders", "Xem đơn hàng", "Xem danh sách, chi tiết và thống kê đơn hàng trong doanh nghiệp", false),
    ORDER_CREATE("order:create", "orders", "Tạo đơn hàng", "Tạo đơn hàng mới và nhập hàng loạt đơn hàng từ CSV/Excel", false),
    ORDER_UPDATE("order:update", "orders", "Cập nhật đơn hàng", "Chỉnh sửa thông tin đơn hàng và gán tài xế thủ công", false),
    ORDER_CANCEL("order:cancel", "orders", "Hủy đơn hàng", "Hủy đơn hàng của doanh nghiệp", false),
    ORDER_DELIVER("order:deliver", "orders", "Cập nhật giao nhận (Tài xế)", "Xác nhận lấy hàng, giao hàng thành công hoặc báo giao thất bại trên ứng dụng tài xế", true),

    // Dispatch & Routing
    DISPATCH_READ("dispatch:read", "dispatch", "Xem điều phối & lộ trình", "Xem kế hoạch điều phối, phiên tối ưu lộ trình và kết quả phân tuyến", false),
    DISPATCH_EXECUTE("dispatch:execute", "dispatch", "Chạy điều phối & tối ưu lộ trình", "Khởi chạy tối ưu lộ trình hàng loạt (VRP), chèn đơn động và duyệt kế hoạch điều phối", false),

    // Fleet (Drivers & Vehicles)
    DRIVER_READ("driver:read", "fleet", "Xem đội ngũ tài xế", "Xem danh sách, hồ sơ và vị trí tài xế trong doanh nghiệp", false),
    DRIVER_MANAGE("driver:manage", "fleet", "Quản lý đội ngũ tài xế", "Tạo hồ sơ tài xế, duyệt KYC và phân công phương tiện cho tài xế", false),
    DRIVER_SELF("driver:self", "fleet", "Tác nghiệp tài xế cá nhân", "Cập nhật trạng thái trực tuyến, vị trí GPS và hồ sơ cá nhân của tài xế", true),
    VEHICLE_READ("vehicle:read", "fleet", "Xem phương tiện", "Xem danh sách đội xe và thông số tải trọng của doanh nghiệp", false),
    VEHICLE_MANAGE("vehicle:manage", "fleet", "Quản lý phương tiện", "Thêm mới, cập nhật và quản lý trạng thái đội xe của doanh nghiệp", false),

    // Warehouses & Hubs
    HUB_READ("hub:read", "warehouses", "Xem kho & bưu cục", "Xem danh sách kho hàng, điểm trung chuyển (Hub/Depot) và tồn kho", false),
    HUB_MANAGE("hub:manage", "warehouses", "Quản lý kho & bưu cục", "Thêm mới, cấu hình kho hàng và điều chỉnh sức chứa kho", false),

    // Analytics & SLA
    ANALYTICS_READ("analytics:read", "analytics", "Xem báo cáo & phân tích", "Xem bảng điều khiển KPI, biểu đồ hiệu suất giao hàng và dự báo AI", false),
    SLA_READ("sla:read", "sla", "Xem cảnh báo SLA", "Xem danh sách vi phạm cam kết thời gian giao hàng (SLA)", false),
    SLA_MANAGE("sla:manage", "sla", "Quản lý cấu hình SLA", "Thiết lập quy tắc SLA và xử lý cảnh báo vi phạm SLA", false),

    // Members & Roles
    MEMBER_READ("member:read", "members", "Xem thành viên doanh nghiệp", "Xem danh sách thành viên, lời mời và quyền hạn trong doanh nghiệp", false),
    MEMBER_INVITE("member:invite", "members", "Mời thành viên mới", "Gửi lời mời tham gia doanh nghiệp và thu hồi lời mời đang chờ", false),
    MEMBER_MANAGE("member:manage", "members", "Quản lý thành viên & phân quyền", "Cập nhật vai trò, tùy chỉnh quyền hạn từng thành viên hoặc xóa thành viên cấp dưới", false),

    // Tenant Settings, Billing & Integrations
    TENANT_SETTINGS_READ("tenant:settings:read", "settings", "Xem cấu hình doanh nghiệp", "Xem hồ sơ doanh nghiệp, cấu hình vận hành và danh sách webhook", false),
    TENANT_SETTINGS_MANAGE("tenant:settings:manage", "settings", "Quản lý cấu hình doanh nghiệp", "Chỉnh sửa thông tin doanh nghiệp, khu vực hoạt động và quy tắc vận hành", false),
    BILLING_READ("billing:read", "billing", "Xem gói cước & hóa đơn", "Xem gói dịch vụ hiện tại, hạn mức sử dụng và lịch sử thanh toán", false),
    BILLING_MANAGE("billing:manage", "billing", "Quản lý gói cước & thanh toán", "Nâng cấp gói dịch vụ, gia hạn thuê bao và thực hiện thanh toán", false),
    API_KEY_READ("apikey:read", "integrations", "Xem khóa API & tích hợp", "Xem danh sách API key và lịch sử đồng bộ đơn hàng tự động", false),
    API_KEY_MANAGE("apikey:manage", "integrations", "Quản lý khóa API & tích hợp", "Tạo mới, xoay vòng hoặc thu hồi API key kết nối hệ thống bên ngoài", false),
    WEBHOOK_MANAGE("webhook:manage", "integrations", "Quản lý Webhook", "Đăng ký, chỉnh sửa và kiểm thử các điểm cuối nhận sự kiện Webhook", false),

    // Notifications & Support
    NOTIFICATION_READ("notification:read", "notifications", "Xem thông báo", "Xem danh sách thông báo vận hành và cảnh báo hệ thống", false),
    SUPPORT_ACCESS("support:access", "support", "Sử dụng trợ lý & hỗ trợ", "Tạo yêu cầu hỗ trợ và sử dụng trợ lý AI tra cứu vận hành", false);

    private static final Map<String, TenantPermission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TenantPermission::getCode, p -> p));

    private static final List<String> ALL_CODES = Arrays.stream(values())
            .map(TenantPermission::getCode)
            .toList();

    private static final Map<TenantMembershipRole, List<String>> DEFAULT_ROLE_PERMISSIONS;

    static {
        Map<TenantMembershipRole, List<String>> map = new EnumMap<>(TenantMembershipRole.class);

        // OWNER always has all 29 permissions
        map.put(TenantMembershipRole.OWNER, ALL_CODES);

        // TENANT_ADMIN: all operational, member, and integration permissions by default (except billing:manage)
        map.put(TenantMembershipRole.TENANT_ADMIN, List.of(
                ORDER_READ.code, ORDER_CREATE.code, ORDER_UPDATE.code, ORDER_CANCEL.code,
                DISPATCH_READ.code, DISPATCH_EXECUTE.code,
                DRIVER_READ.code, DRIVER_MANAGE.code,
                VEHICLE_READ.code, VEHICLE_MANAGE.code,
                HUB_READ.code, HUB_MANAGE.code,
                ANALYTICS_READ.code, SLA_READ.code, SLA_MANAGE.code,
                MEMBER_READ.code, MEMBER_INVITE.code, MEMBER_MANAGE.code,
                TENANT_SETTINGS_READ.code, TENANT_SETTINGS_MANAGE.code,
                BILLING_READ.code,
                API_KEY_READ.code, API_KEY_MANAGE.code, WEBHOOK_MANAGE.code,
                NOTIFICATION_READ.code, SUPPORT_ACCESS.code
        ));

        // OPERATOR: daily operations, orders, dispatch, fleet & hub management
        map.put(TenantMembershipRole.OPERATOR, List.of(
                ORDER_READ.code, ORDER_CREATE.code, ORDER_UPDATE.code, ORDER_CANCEL.code,
                DISPATCH_READ.code, DISPATCH_EXECUTE.code,
                DRIVER_READ.code, DRIVER_MANAGE.code,
                VEHICLE_READ.code, VEHICLE_MANAGE.code,
                HUB_READ.code, HUB_MANAGE.code,
                ANALYTICS_READ.code, SLA_READ.code,
                MEMBER_READ.code,
                TENANT_SETTINGS_READ.code,
                NOTIFICATION_READ.code, SUPPORT_ACCESS.code
        ));

        // DRIVER: delivery execution, self-management, and dispatch/simulation visibility
        map.put(TenantMembershipRole.DRIVER, List.of(
                ORDER_READ.code, ORDER_DELIVER.code,
                DISPATCH_READ.code, DISPATCH_EXECUTE.code,
                DRIVER_READ.code, DRIVER_SELF.code,
                VEHICLE_READ.code,
                HUB_READ.code,
                NOTIFICATION_READ.code, SUPPORT_ACCESS.code
        ));

        // VIEWER: read-only visibility across operations and analytics
        map.put(TenantMembershipRole.VIEWER, List.of(
                ORDER_READ.code,
                DISPATCH_READ.code,
                DRIVER_READ.code,
                VEHICLE_READ.code,
                HUB_READ.code,
                ANALYTICS_READ.code,
                SLA_READ.code,
                MEMBER_READ.code,
                TENANT_SETTINGS_READ.code,
                NOTIFICATION_READ.code
        ));

        DEFAULT_ROLE_PERMISSIONS = Collections.unmodifiableMap(map);
    }

    private final String code;
    private final String module;
    private final String displayName;
    private final String description;
    private final boolean driverOnly;

    TenantPermission(String code, String module, String displayName, String description, boolean driverOnly) {
        this.code = code;
        this.module = module;
        this.displayName = displayName;
        this.description = description;
        this.driverOnly = driverOnly;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDriverOnly() {
        return driverOnly;
    }

    public static Optional<TenantPermission> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code.trim().toLowerCase()));
    }

    public static boolean isValidCode(String code) {
        return fromCode(code).isPresent();
    }

    public static List<String> allCodes() {
        return ALL_CODES;
    }

    public static List<String> defaultPermissionsForRole(TenantMembershipRole role) {
        if (role == null) {
            return List.of();
        }
        return DEFAULT_ROLE_PERMISSIONS.getOrDefault(role, List.of());
    }

    public static List<String> defaultPermissionsForRoleName(String roleName, Role systemRole) {
        if (systemRole == Role.SYSTEM_ADMIN || "SYSTEM_ADMIN".equalsIgnoreCase(roleName)) {
            return ALL_CODES;
        }
        if (roleName == null || roleName.isBlank()) {
            return List.of();
        }
        try {
            TenantMembershipRole membershipRole = TenantMembershipRole.valueOf(roleName.trim().toUpperCase());
            return defaultPermissionsForRole(membershipRole);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    public static Set<String> normalizeAndValidateCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawCodes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            TenantPermission perm = fromCode(raw)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown permission code: " + raw));
            normalized.add(perm.getCode());
        }
        return normalized;
    }

    /**
     * Numerical rank for role hierarchy enforcement:
     * OWNER (50) > TENANT_ADMIN (40) > OPERATOR (30) > DRIVER (20) > VIEWER (10).
     */
    public static int roleRank(TenantMembershipRole role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case OWNER -> 50;
            case TENANT_ADMIN -> 40;
            case OPERATOR -> 30;
            case DRIVER -> 20;
            case VIEWER -> 10;
        };
    }
}
