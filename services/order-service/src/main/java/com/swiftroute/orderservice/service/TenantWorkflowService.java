package com.swiftroute.orderservice.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.dto.request.RegisterTenantRequest;
import com.swiftroute.orderservice.dto.response.TenantResponse;
import com.swiftroute.orderservice.entity.AuditLog;
import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.DeliveryMode;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantWorkflowService {
    private final TenantRepository tenants;
    private final OutboxEventRepository events;
    private final AuditLogRepository audit;
    private final BusinessTypeProfileRegistry profiles;
    private final OperationalReadinessService readiness;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional
    public Tenant create(RegisterTenantRequest request, Map<String, Object> identity, String key) {
        if (!Boolean.TRUE.equals(identity.get("email_verified")))
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        if (key == null || key.isBlank() || key.length() > 128)
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        UUID owner = UUID.fromString(identity.get("user_id").toString());
        String fingerprint = encode(request);
        jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) (owner + ":" + key).hashCode() + ")");
        var existing = jdbc.queryForList(
                "SELECT tenant_id,request_payload FROM tenant_registration_keys WHERE owner_user_id=? AND idempotency_key=?",
                owner, key);
        if (!existing.isEmpty()) {
            if (!fingerprint.equals(existing.getFirst().get("request_payload")))
                throw new AppException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return find((UUID) existing.getFirst().get("tenant_id"));
        }
        if (tenants.existsByBusinessNameIgnoreCase(request.businessName().trim()))
            throw new AppException(ErrorCode.TENANT_ALREADY_EXISTS);
        Tenant tenant = Tenant.builder().ownerUserId(owner).businessName(request.businessName().trim())
                .businessType(request.businessType())
                .address(request.address().trim()).contactPhone(request.contactPhone().trim())
                .contactEmail(identity.get("email").toString())
                .countryCode(request.countryCode()).status(TenantStatus.PENDING).build();
        profiles.applyProfile(tenant, request.businessType());
        tenant.setDeliverySettings(defaultSettings(request.businessType()));
        tenant = tenants.saveAndFlush(tenant);
        jdbc.update(
                "INSERT INTO tenant_registration_keys(owner_user_id,idempotency_key,tenant_id,request_payload) VALUES (?,?,?,?)",
                owner, key, tenant.getId(), fingerprint);
        jdbc.update("INSERT INTO onboarding_progress(tenant_id) VALUES (?) ON CONFLICT DO NOTHING", tenant.getId());
        jdbc.update("INSERT INTO tenant_provisioning(tenant_id,owner_user_id) VALUES (?,?)", tenant.getId(), owner);
        emit(tenant, "tenant.created.v1", owner, Map.of());
        emit(tenant, "tenant.created", owner, Map.of()); // existing Auth consumers remain compatible
        return tenant;
    }

    private Map<String, Object> defaultSettings(BusinessType type) {
        boolean food = type == BusinessType.RESTAURANT || type == BusinessType.CAFE;
        return new LinkedHashMap<>(Map.of("preparation_enabled", food, "preparation_minutes", food ? 15 : 0,
                "delivery_verification_required", true, "sensitive_handling", type == BusinessType.PHARMACY,
                "service_priority", "BALANCED", "routing_approval_required", false));
    }

    @Transactional(readOnly = true)
    public Tenant find(UUID id) {
        return tenants.findById(id).orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
    }

    public record Setup(String businessName, BusinessType businessType, String contactPhone, String address,
            String countryCode,
            DeliveryMode deliveryMode, Map<String, Object> deliverySettings) {
    }

    @Transactional
    public Tenant configure(UUID id, Setup update) {
        Tenant tenant = find(id);
        boolean profileChange = update.businessName() != null || update.businessType() != null
                || update.contactPhone() != null || update.address() != null || update.countryCode() != null;
        boolean reviewing = jdbc.queryForObject(
                "SELECT count(*) FROM tenant_verification_requests WHERE tenant_id=? AND status IN ('SUBMITTED','UNDER_REVIEW') AND closed_at IS NULL",
                Integer.class, id) > 0;
        if ((profileChange && tenant.getStatus() == TenantStatus.APPROVED) || reviewing)
            throw new AppException(ErrorCode.INVALID_VERIFICATION_TRANSITION);
        if (update.businessName() != null) {
            if (update.businessName().trim().length() < 2 || update.businessName().length() > 255)
                invalid();
            tenant.setBusinessName(update.businessName().trim());
        }
        if (update.businessType() != null)
            tenant.setBusinessType(update.businessType());
        if (update.contactPhone() != null) {
            if (!update.contactPhone().matches("[+0-9 ()-]{9,20}"))
                invalid();
            tenant.setContactPhone(update.contactPhone().trim());
        }
        if (update.address() != null) {
            if (update.address().trim().length() < 5 || update.address().length() > 500)
                invalid();
            tenant.setAddress(update.address().trim());
        }
        if (update.countryCode() != null) {
            if (!update.countryCode().matches("[A-Z]{2}"))
                invalid();
            tenant.setCountryCode(update.countryCode());
        }
        if (update.deliveryMode() != null) {
            tenant.setDeliveryMode(update.deliveryMode());
            tenant.setModeConfirmed(true);
        }
        if (update.deliverySettings() != null) {
            Map<String, Object> settings = new LinkedHashMap<>(tenant.getDeliverySettings());
            for (var entry : update.deliverySettings().entrySet()) {
                if (!Set.of("preparation_enabled", "preparation_minutes", "delivery_verification_required",
                        "sensitive_handling", "service_priority", "routing_approval_required").contains(entry.getKey()))
                    invalid();
                Object v = entry.getValue();
                if (entry.getKey().equals("preparation_minutes")) {
                    if (!(v instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 240
                            || n.doubleValue() != n.intValue())
                        invalid();
                } else if (entry.getKey().equals("service_priority")) {
                    if (!Set.of("BALANCED", "FASTEST", "LOWEST_COST").contains(String.valueOf(v)))
                        invalid();
                } else if (!(v instanceof Boolean))
                    invalid();
                settings.put(entry.getKey(), v);
            }
            tenant.setDeliverySettings(settings);
            tenant.setSettingsConfirmed(true);
            String priority = String.valueOf(settings.getOrDefault("service_priority", "BALANCED"));
            // Business language maps to the existing supported time/cost objective; no new
            // solver knob.
            tenant.setOptimizationWeights(switch (priority) {
                case "FASTEST" -> new OptimizationWeights(0.8, 0.2);
                case "LOWEST_COST" -> new OptimizationWeights(0.2, 0.8);
                default -> new OptimizationWeights(0.5, 0.5);
            });
            var dispatch = tenant.getDispatchConfig() == null ? new TenantDispatchConfig() : tenant.getDispatchConfig();
            dispatch.setRequireApproval(Boolean.TRUE.equals(settings.get("routing_approval_required")));
            tenant.setDispatchConfig(dispatch);
        }
        tenants.saveAndFlush(tenant);
        onboarding(id);
        return tenant;
    }

    @Transactional
    public Map<String, Object> onboarding(UUID id) {
        Tenant tenant = find(id);
        LinkedHashMap<String, Boolean> steps = new LinkedHashMap<>();
        steps.put("BUSINESS_PROFILE", profileComplete(tenant));
        steps.put("PICKUP_POINT", readiness.hasPickup(tenant));
        steps.put("DELIVERY_MODE", tenant.isModeConfirmed());
        steps.put("DELIVERY_SETTINGS", tenant.isSettingsConfirmed());
        boolean submitted = jdbc.queryForObject(
                "SELECT count(*) FROM tenant_verification_requests WHERE tenant_id=? AND status IN ('SUBMITTED','UNDER_REVIEW','APPROVED')",
                Integer.class, id) > 0;
        steps.put("VERIFICATION_SUBMISSION", submitted);
        List<String> completed = steps.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        List<String> incomplete = steps.entrySet().stream().filter(x -> !x.getValue()).map(Map.Entry::getKey).toList();
        String status = incomplete.isEmpty() ? "COMPLETED" : completed.isEmpty() ? "NOT_STARTED" : "IN_PROGRESS";
        jdbc.update(
                "INSERT INTO onboarding_progress(tenant_id,status,completed_steps,updated_at) VALUES (?,?,?::jsonb,now()) ON CONFLICT (tenant_id) DO UPDATE SET status=excluded.status,completed_steps=excluded.completed_steps,updated_at=now()",
                id, status, encode(completed));
        return Map.of("tenant_id", id, "status", status, "completed_steps", completed, "incomplete_steps", incomplete,
                "next_action", incomplete.isEmpty() ? "VIEW_VERIFICATION" : incomplete.getFirst(), "tenant",
                TenantResponse.fromEntity(tenant),
                "profile", profile(tenant), "verification_requests", history(id), "readiness", readiness.check(tenant));
    }

    private boolean profileComplete(Tenant t) {
        return t.getBusinessName() != null && !t.getBusinessName().isBlank() && t.getAddress() != null
                && !t.getAddress().isBlank() && t.getContactPhone() != null && !t.getContactPhone().isBlank()
                && t.getCountryCode() != null;
    }

    private Map<String, Object> profile(Tenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("business_name", t.getBusinessName());
        m.put("business_type", t.getBusinessType());
        m.put("contact_phone", t.getContactPhone());
        m.put("address", t.getAddress());
        m.put("country_code", t.getCountryCode());
        m.put("delivery_mode", t.getDeliveryMode());
        m.put("delivery_settings", t.getDeliverySettings());
        return m;
    }

    private Map<String, Object> submissionSnapshot(Tenant tenant) {
        Map<String, Object> snapshot = new LinkedHashMap<>(profile(tenant));
        snapshot.put("schema_version", 2);
        snapshot.put("pickup_points", jdbc.queryForList(
                "SELECT id,name,address,lat,lng,contact_name,contact_phone,is_default FROM pickup_locations WHERE tenant_id=? AND status='ACTIVE' ORDER BY is_default DESC,created_at",
                tenant.getId()));
        if (tenant.getPickupLat() != null && tenant.getPickupLng() != null)
            snapshot.put("legacy_pickup", Map.of("address", Objects.toString(tenant.getAddress(), ""), "lat",
                    tenant.getPickupLat(), "lng", tenant.getPickupLng()));
        return snapshot;
    }

    @Transactional(readOnly = true)
    public com.swiftroute.orderservice.dto.response.PageResponse<Map<String, Object>> queue(String status,
            String search, int page, int size) {
        if (status != null && !status.isBlank() && !Set
                .of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUIRED", "APPROVED", "REJECTED").contains(status))
            invalid();
        if (page < 0 || page > 100000 || size < 1 || size > 100 || (search != null && search.length() > 200))
            invalid();
        String selected = status == null || status.isBlank() ? null : status;
        String term = search == null ? "" : search.trim();
        String where = " FROM tenant_verification_requests r JOIN tenants t USING(tenant_id) WHERE (?::varchar IS NULL OR r.status=?) AND strpos(lower(t.business_name),lower(?))>0";
        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, selected, selected, term);
        var content = jdbc.queryForList(
                "SELECT r.request_id,r.tenant_id,r.status,r.submitted_at,r.reviewed_at,r.reason,r.version,t.business_name"
                        + where + " ORDER BY r.created_at DESC,r.request_id LIMIT ? OFFSET ?",
                selected, selected, term, size, (long) page * size);
        int pages = (int) Math.ceil((double) total / size);
        return new com.swiftroute.orderservice.dto.response.PageResponse<>(content, page, size, total, pages, page == 0,
                page + 1 >= pages);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(UUID requestId) {
        var request = request(requestId);
        UUID tenantId = (UUID) request.get("tenant_id");
        return Map.of("request", request, "tenant", TenantResponse.fromEntity(find(tenantId)), "history",
                history(tenantId),
                "timeline",
                jdbc.queryForList(
                        "SELECT action,actor_id,created_at,details::text AS details FROM audit_logs WHERE tenant_id=? AND aggregate_type='TENANT_VERIFICATION' ORDER BY created_at DESC",
                        tenantId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID id) {
        return jdbc.queryForList(
                "SELECT request_id,tenant_id,status,submitted_at,reviewer_id,reviewed_at,reason,version,snapshot::text AS snapshot FROM tenant_verification_requests WHERE tenant_id=? ORDER BY created_at DESC",
                id);
    }

    @Transactional
    public Map<String, Object> submit(UUID id, UUID actor, String key) {
        if (key == null || key.isBlank() || key.length() > 128)
            invalid();
        jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) id.hashCode() + ")");
        Tenant t = find(id);
        var duplicate = jdbc.queryForList(
                "SELECT request_id FROM tenant_verification_requests WHERE tenant_id=? AND submission_key=?", id, key);
        if (!duplicate.isEmpty())
            return request((UUID) duplicate.getFirst().get("request_id"));
        if (t.getStatus() != TenantStatus.PENDING && t.getStatus() != TenantStatus.REJECTED)
            throw new AppException(ErrorCode.INVALID_VERIFICATION_TRANSITION);
        var open = jdbc.queryForList(
                "SELECT request_id,status FROM tenant_verification_requests WHERE tenant_id=? AND status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','CHANGES_REQUIRED') AND closed_at IS NULL",
                id);
        if (!open.isEmpty() && !"CHANGES_REQUIRED".equals(open.getFirst().get("status")))
            return request((UUID) open.getFirst().get("request_id"));
        if (!profileComplete(t) || !readiness.hasPickup(t) || !t.isModeConfirmed() || !t.isSettingsConfirmed())
            throw new AppException(ErrorCode.VERIFICATION_INCOMPLETE);
        // Close the prior changes-required attempt without deleting its snapshot or
        // decision.
        if (!open.isEmpty())
            jdbc.update(
                    "UPDATE tenant_verification_requests SET status='CHANGES_REQUIRED',closed_at=now() WHERE request_id=?",
                    open.getFirst().get("request_id"));
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenant_verification_requests(request_id,tenant_id,status,submission_key,submitted_by,submitted_at,snapshot) VALUES (?,?,'SUBMITTED',?,?,now(),?::jsonb)",
                requestId, id, key, actor, encode(submissionSnapshot(t)));
        t.setStatus(TenantStatus.PENDING);
        tenants.saveAndFlush(t);
        audit(t, requestId, "VERIFICATION_SUBMITTED", actor, Map.of());
        emit(t, "tenant.verification_submitted.v1", actor, Map.of("requestId", requestId));
        return request(requestId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> request(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT request_id,tenant_id,status,submitted_at,reviewer_id,reviewed_at,reason,version,snapshot::text AS snapshot FROM tenant_verification_requests WHERE request_id=?",
                id);
        if (rows.isEmpty())
            throw new AppException(ErrorCode.VERIFICATION_NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reviews(String status) {
        if (status != null && !Set.of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUIRED", "APPROVED", "REJECTED")
                .contains(status))
            invalid();
        return jdbc.queryForList(
                "SELECT r.request_id,r.tenant_id,r.status,r.submitted_at,r.reviewer_id,r.reviewed_at,r.reason,r.version,r.snapshot::text AS snapshot,t.business_name FROM tenant_verification_requests r JOIN tenants t USING(tenant_id) WHERE (?::varchar IS NULL OR r.status=?) ORDER BY r.created_at DESC LIMIT 200",
                status, status);
    }

    public record Decision(String status, String reason, Long version) {
    }

    @Transactional
    public Map<String, Object> review(UUID id, Decision decision, UUID reviewer) {
        if (decision.version() == null || !Set.of("UNDER_REVIEW", "APPROVED", "REJECTED", "CHANGES_REQUIRED")
                .contains(String.valueOf(decision.status())))
            invalid();
        if (Set.of("REJECTED", "CHANGES_REQUIRED").contains(decision.status())
                && (decision.reason() == null || decision.reason().isBlank()))
            invalid();
        if (decision.reason() != null && decision.reason().length() > 2000)
            invalid();
        var current = request(id);
        if (!Set.of("SUBMITTED", "UNDER_REVIEW").contains(current.get("status")))
            throw new AppException(ErrorCode.INVALID_VERIFICATION_TRANSITION);
        UUID tenantId = (UUID) current.get("tenant_id");
        jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");
        Tenant t = find(tenantId);
        if (t.getStatus() == TenantStatus.SUSPENDED)
            throw new AppException(ErrorCode.INVALID_VERIFICATION_TRANSITION);
        int changed = jdbc.update(
                "UPDATE tenant_verification_requests SET status=?,reviewer_id=?,reviewed_at=now(),reason=?,version=version+1 WHERE request_id=? AND version=? AND status IN ('SUBMITTED','UNDER_REVIEW')",
                decision.status(), reviewer, decision.reason(), id, decision.version());
        if (changed != 1)
            throw new AppException(ErrorCode.INVALID_VERIFICATION_TRANSITION);
        if ("APPROVED".equals(decision.status())) {
            t.setStatus(TenantStatus.APPROVED);
            t.setApprovedBy(reviewer);
            t.setApprovedAt(LocalDateTime.now(Clock.systemUTC()));
        } else if ("REJECTED".equals(decision.status()))
            t.setStatus(TenantStatus.REJECTED);
        else if ("CHANGES_REQUIRED".equals(decision.status()))
            t.setStatus(TenantStatus.PENDING);
        t.setApprovalReason(decision.reason() == null ? null
                : decision.reason().substring(0, Math.min(500, decision.reason().length())));
        tenants.saveAndFlush(t);
        audit(t, id, "VERIFICATION_" + decision.status(), reviewer,
                Map.of("reason", decision.reason() == null ? "" : decision.reason()));
        String event = switch (decision.status()) {
            case "APPROVED" -> "tenant.approved.v1";
            case "REJECTED" -> "tenant.rejected.v1";
            case "CHANGES_REQUIRED" -> "tenant.verification_changes_requested.v1";
            default -> "tenant.verification_under_review.v1";
        };
        emit(t, event, reviewer, Map.of("requestId", id, "reason", decision.reason() == null ? "" : decision.reason()));
        if ("APPROVED".equals(decision.status()))
            emit(t, "tenant.approved", reviewer, Map.of());
        return request(id);
    }

    @Transactional
    public Tenant suspend(UUID id, String reason, UUID actor, boolean reactivate) {
        Tenant t = find(id);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            invalid();
        if (reactivate) {
            if (t.getStatus() != TenantStatus.SUSPENDED || t.getApprovedAt() == null)
                throw new AppException(ErrorCode.INVALID_TENANT_STATUS_TRANSITION);
            t.setStatus(TenantStatus.APPROVED);
            t.setSuspendedAt(null);
        } else {
            if (t.getStatus() != TenantStatus.APPROVED)
                throw new AppException(ErrorCode.INVALID_TENANT_STATUS_TRANSITION);
            t.setStatus(TenantStatus.SUSPENDED);
            t.setSuspendedAt(LocalDateTime.now(Clock.systemUTC()));
        }
        t.setApprovalReason(reason);
        tenants.saveAndFlush(t);
        audit(t, id, reactivate ? "TENANT_REACTIVATED" : "TENANT_SUSPENDED", actor, Map.of("reason", reason));
        emit(t, reactivate ? "tenant.approved.v1" : "tenant.suspended.v1", actor, Map.of("reason", reason));
        return t;
    }

    private void audit(Tenant t, UUID id, String action, UUID actor, Map<String, Object> details) {
        audit.save(AuditLog.builder().tenantId(t.getId()).aggregateType("TENANT_VERIFICATION").aggregateId(id)
                .action(action).actorId(actor).details(details).build());
    }

    private void emit(Tenant t, String type, UUID actor, Map<String, Object> extra) {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("eventId", id);
        payload.put("eventType", type);
        payload.put("tenantId", t.getId());
        payload.put("ownerUserId", t.getOwnerUserId());
        payload.put("actorId", actor);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", t.getVersion());
        payload.put("payloadVersion", 1);
        if (t.getContactEmail() != null)
            payload.put("email", t.getContactEmail());
        events.save(OutboxEvent.builder().eventId(id).aggregateId(t.getId()).aggregateType("tenant").eventType(type)
                .payload(payload).build());
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void invalid() {
        throw new AppException(ErrorCode.VALIDATION_ERROR);
    }
}
