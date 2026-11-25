package com.swiftroute.notificationservice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes tenant workflow and account events from RabbitMQ and stores
 * in-app notifications, broadcasting updates via SSE to connected operators.
 */
@Component
@Slf4j
public class TenantNotificationConsumer {
    static final String IMPORT_COMPLETED = "tenant.operation_flow_import_completed.v1";
    private static final Set<String> SENSITIVE_AUTH_EVENTS = Set.of(
            "user.email_verification.v1",
            "user.password_reset_requested.v1",
            "tenant.driver_invited.v1"
    );

    private final JdbcTemplate jdbc;
    private final NotificationSseService sseService;
    private final EmailNotificationSender emailSender;
    private final ObjectMapper json = new ObjectMapper();

    public TenantNotificationConsumer(JdbcTemplate jdbc, @Autowired(required = false) NotificationSseService sseService,
            EmailNotificationSender emailSender) {
        this.jdbc = jdbc;
        this.sseService = sseService;
        this.emailSender = emailSender;
    }

    @RabbitListener(queues = TenantNotificationConfig.QUEUE)
    @Transactional
    public void receive(Message message) throws Exception {
        var p = json.readTree(message.getBody());
        String type = p.path("eventType").asText(p.path("event_type").asText());
        if ("tenant.member_permissions_updated.v1".equals(type)) {
            handleMemberPermissionsUpdated(p);
            return;
        }
        if (!Set.of("tenant.driver_invited.v1", "user.email_verification.v1", "user.password_reset_requested.v1",
                "tenant.created", "tenant.created.v1", "tenant.verification_submitted.v1",
                "tenant.verification_under_review.v1", "tenant.verification_changes_requested.v1",
                "tenant.approved", "tenant.approved.v1", "tenant.rejected.v1", "tenant.suspended.v1",
                IMPORT_COMPLETED).contains(type)) {
            return;
        }

        UUID eventId = UUID.fromString(p.path("eventId").asText(p.path("event_id").asText()));
        if (IMPORT_COMPLETED.equals(type) && !p.hasNonNull("tenantId")) {
            log.warn("Ignoring Operation Flow import completion without a tenant: {}", eventId);
            return;
        }
        if (jdbc.update(
                "INSERT INTO processed_events(event_id, consumer_name) VALUES (?, 'tenant-notifications') ON CONFLICT DO NOTHING",
                eventId) == 0) {
            return;
        }

        String user = p.path("userId").asText(p.path("ownerUserId").asText());
        UUID tenant = p.hasNonNull("tenantId") ? UUID.fromString(p.get("tenantId").asText()) : null;

        String title = switch (type) {
            case "user.email_verification.v1" -> "Xác minh tài khoản SwiftRoute";
            case "user.password_reset_requested.v1" -> "Đặt lại mật khẩu SwiftRoute";
            case "tenant.driver_invited.v1" -> "Lời mời tham gia đội ngũ giao hàng";
            case "tenant.created", "tenant.created.v1" -> "Khởi tạo doanh nghiệp thành công";
            case "tenant.verification_submitted.v1" -> "Hồ sơ xác minh doanh nghiệp đã được gửi";
            case "tenant.verification_under_review.v1" -> "Hồ sơ xác minh đang được thẩm định";
            case "tenant.verification_changes_requested.v1" -> "Yêu cầu bổ sung hồ sơ xác minh doanh nghiệp";
            case "tenant.approved", "tenant.approved.v1" -> "Hồ sơ doanh nghiệp đã được phê duyệt";
            case "tenant.rejected.v1" -> "Hồ sơ doanh nghiệp không được phê duyệt";
            case "tenant.suspended.v1" -> "Doanh nghiệp đã bị tạm ngưng hoạt động";
            case IMPORT_COMPLETED -> "Đã chạy nhập vận đơn tự động";
            default -> "Thông báo doanh nghiệp";
        };

        String body = switch (type) {
            case "user.email_verification.v1" ->
                "Tài khoản đăng ký: " + p.path("email").asText()
                + "\nLiên kết xác thực: " + p.path("verificationUrl").asText();
            case "tenant.driver_invited.v1" -> {
                String businessName = p.path("businessName").asText("Doanh nghiệp SwiftRoute");
                yield businessName + " đã gửi lời mời tham gia đội ngũ giao hàng."
                    + "\nLiên kết chấp nhận: " + p.path("verificationUrl").asText();
            }
            case "user.password_reset_requested.v1" ->
                "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản " + p.path("email").asText()
                + ".\nLiên kết có hiệu lực trong 15 phút: " + p.path("verificationUrl").asText()
                + "\nNếu bạn không yêu cầu, hãy bỏ qua email này.";
            case "tenant.created", "tenant.created.v1" ->
                "Không gian làm việc cho doanh nghiệp đã sẵn sàng. Hãy hoàn thiện hồ sơ xác minh để bắt đầu vận hành.";
            case "tenant.verification_submitted.v1" ->
                "Hồ sơ xác minh doanh nghiệp đã được tiếp nhận và đang chờ ban quản trị phê duyệt. Hệ thống sẽ thông báo ngay khi có kết quả.";
            case "tenant.verification_under_review.v1" ->
                "Đội ngũ quản trị đang thẩm định thông tin và hồ sơ đăng ký của doanh nghiệp.";
            case "tenant.verification_changes_requested.v1" -> {
                String reason = p.path("reason").asText("").trim();
                yield reason.isBlank()
                    ? "Ban quản trị yêu cầu cập nhật, bổ sung thông tin hồ sơ doanh nghiệp trước khi phê duyệt."
                    : "Yêu cầu bổ sung thông tin hồ sơ: " + reason;
            }
            case "tenant.approved", "tenant.approved.v1" ->
                "Hồ sơ doanh nghiệp đã được phê duyệt thành công. Bạn hiện có thể tạo đơn hàng và bắt đầu điều phối giao vận.";
            case "tenant.rejected.v1" -> {
                String reason = p.path("reason").asText("").trim();
                yield reason.isBlank()
                    ? "Hồ sơ xác minh doanh nghiệp đã bị từ chối phê duyệt. Vui lòng liên hệ ban quản trị để được hỗ trợ."
                    : "Hồ sơ doanh nghiệp không được phê duyệt. Lý do: " + reason;
            }
            case "tenant.suspended.v1" -> {
                String reason = p.path("reason").asText("").trim();
                yield reason.isBlank()
                    ? "Tài khoản doanh nghiệp đã bị tạm ngưng hoạt động. Vui lòng liên hệ hỗ trợ SwiftRoute để biết thêm chi tiết."
                    : "Tài khoản doanh nghiệp tạm ngưng hoạt động. Lý do: " + reason;
            }
            case IMPORT_COMPLETED -> "Đã tạo %d vận đơn. Cần bổ sung địa chỉ: %d. Trùng mã: %d. Dòng lỗi: %d."
                .formatted(p.path("created").asInt(), p.path("needsAddress").asInt(),
                    p.path("duplicates").asInt(), p.path("invalid").asInt());
            default -> {
                String reason = p.path("reason").asText("").trim();
                yield reason.isBlank()
                    ? "Không gian làm việc vừa có thông tin cập nhật mới."
                    : "Cập nhật thông tin doanh nghiệp: " + reason;
            }
        };

        log.info("Recorded in-app tenant notification [{}]: {} for user/tenant {}",
                type, title, user != null && !user.isBlank() ? user : tenant);

        UUID recipientId = (user != null && !user.isBlank()) ? UUID.fromString(user) : tenant;
        if (recipientId == null) {
            return;
        }

        jdbc.update("""
                INSERT INTO notification_deliveries(
                    delivery_id, tenant_id, user_id, channel, template_code, title, body, status, sent_at
                ) VALUES (?, ?, ?, 'IN_APP', ?, ?, ?, 'SENT', now())
                """,
                eventId, tenant, recipientId, type, title, body);

        if (SENSITIVE_AUTH_EVENTS.contains(type)) {
            emailSender.send(p.path("email").asText(), title, body);
        }

        // Broadcast over SSE to connected tenant operator sessions (skip sensitive token/invitation URLs)
        if (sseService != null && tenant != null && !SENSITIVE_AUTH_EVENTS.contains(type)) {
            String severity = switch (type) {
                case "tenant.suspended.v1" -> "CRITICAL";
                case "tenant.rejected.v1", "tenant.verification_changes_requested.v1" -> "HIGH";
                default -> "LOW";
            };
            String actionUrl = switch (type) {
                case IMPORT_COMPLETED -> OperationalAlertConsumer.resolveActionUrl(type, null, null, null);
                case "tenant.verification_submitted.v1", "tenant.verification_under_review.v1",
                     "tenant.verification_changes_requested.v1", "tenant.rejected.v1", "tenant.suspended.v1" -> "/onboarding";
                case "tenant.approved", "tenant.approved.v1" -> "/orders";
                default -> "/onboarding";
            };

            Map<String, Object> alertDto = new HashMap<>();
            alertDto.put("id", eventId.toString());
            alertDto.put("title", title);
            alertDto.put("body", body);
            alertDto.put("severity", severity);
            alertDto.put("category", "WORKSPACE");
            alertDto.put("action_url", actionUrl);
            alertDto.put("template_code", type);
            alertDto.put("created_at", Instant.now().toString());
            if (IMPORT_COMPLETED.equals(type)) {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sseService.broadcast(tenant, "operational-alert", alertDto);
                        }
                    });
                    return;
                }
            }
            sseService.broadcast(tenant, "operational-alert", alertDto);
        }
    }

    private void handleMemberPermissionsUpdated(com.fasterxml.jackson.databind.JsonNode p) {
        try {
            String tenantStr = p.path("tenantId").asText(p.path("tenant_id").asText(""));
            String userStr = p.path("userId").asText(p.path("user_id").asText(""));
            if (tenantStr.isBlank() || userStr.isBlank()) {
                return;
            }
            UUID tenantId = UUID.fromString(tenantStr.trim());
            UUID userId = UUID.fromString(userStr.trim());
            String role = p.path("membershipRole").asText(p.path("membership_role").asText("OPERATOR"));
            String status = p.path("status").asText("ACTIVE");
            long version = p.path("permissionsVersion").asLong(p.path("permissions_version").asLong(1L));

            java.util.Set<String> perms = new java.util.LinkedHashSet<>();
            var permsNode = p.get("permissions");
            if (permsNode != null && permsNode.isArray()) {
                for (var item : permsNode) {
                    if (item.isTextual() && !item.asText().isBlank()) {
                        perms.add(item.asText().trim().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            NotificationAuthorization.recordLiveSnapshot(tenantId, userId, role, status, perms, version);
            if (sseService != null) {
                Map<String, Object> syncEvent = new HashMap<>();
                syncEvent.put("tenant_id", tenantId.toString());
                syncEvent.put("user_id", userId.toString());
                syncEvent.put("membership_role", role);
                syncEvent.put("status", status);
                syncEvent.put("permissions", java.util.List.copyOf(perms));
                syncEvent.put("permissions_version", version);
                sseService.broadcast(tenantId, "permissions-updated", syncEvent);
            }
        } catch (Exception ex) {
            log.warn("Failed to process member permissions update in notification-service: {}", ex.getMessage());
        }
    }
}
