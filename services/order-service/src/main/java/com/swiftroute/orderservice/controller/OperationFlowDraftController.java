package com.swiftroute.orderservice.controller;

import com.swiftroute.orderservice.dto.OperationFlowSchedule;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.planning.PlanningFlowScheduleService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/orders/operation-flow/drafts")
@RequiredArgsConstructor
public class OperationFlowDraftController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PlanningFlowScheduleService planningSchedule;

    public record Position(double x, double y) {}
    public record Step(@NotNull String id, @NotNull String step, @NotNull @Valid Position position) {}
    public record Link(@NotNull @Size(max = 100) String id, @NotNull String source, @NotNull String target) {}
    public record Draft(
            @NotNull @Size(max = 3) List<@NotNull @Valid Step> nodes,
            @NotNull @Size(max = 2) List<@NotNull @Valid Link> edges,
            @NotNull @Valid OperationFlowSchedule schedule, boolean resolveCoordinates, boolean stopOnCsvErrors,
            UUID pickupLocationId) {
        public Draft(List<Step> nodes, List<Link> edges, OperationFlowSchedule schedule,
                boolean resolveCoordinates, boolean stopOnCsvErrors) {
            this(nodes, edges, schedule, resolveCoordinates, stopOnCsvErrors, null);
        }
    }
    public record Version(@Min(0) int revision, @NotNull @Valid Draft draft) {}
    public record Activation(@NotNull @Min(1) Integer revision) {}
    public record ManualRun(UUID pickupLocationId) {}

    @GetMapping("/planning/activation")
    public ApiResponse<PlanningFlowScheduleService.State> readPlanningSchedule(
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requirePermission("planning", role);
        return ApiResponse.success(planningSchedule.read(tenant));
    }

    @PutMapping("/planning/activation")
    public ApiResponse<PlanningFlowScheduleService.State> activatePlanningSchedule(
            @Valid @RequestBody Activation input,
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        Version version = read("planning", tenant, role).data();
        if (version == null || version.revision() != input.revision()) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
        Draft draft = version.draft();
        List<String> path = pathFor("planning", draft);
        validateDraft(draft, path);
        UUID pickup = draft.pickupLocationId();
        if (draft.nodes().size() != path.size() || draft.edges().size() != path.size() - 1
                || (path.contains("select-orders") && (pickup == null || !planningSchedule.isActivePickup(tenant, pickup)))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return ApiResponse.success(planningSchedule.activate(tenant, version.revision(), draft.schedule(), pickup));
    }

    @DeleteMapping("/planning/activation")
    public ApiResponse<Void> pausePlanningSchedule(
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requirePermission("planning", role);
        planningSchedule.pause(tenant);
        return ApiResponse.success(null);
    }

    @GetMapping("/planning/runs")
    public ApiResponse<PlanningFlowScheduleService.RunPage> listPlanningRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requirePermission("planning", role);
        if (page < 0 || page > 10000) throw new AppException(ErrorCode.VALIDATION_ERROR);
        return ApiResponse.success(planningSchedule.history(tenant, page));
    }

    @PostMapping("/planning/runs")
    public ApiResponse<PlanningFlowScheduleService.Run> runPlanning(
            @RequestBody ManualRun input,
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute("authorizedUserId") UUID actor,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requirePermission("planning", role);
        try {
            return ApiResponse.success(planningSchedule.runManual(tenant, actor, input.pickupLocationId()));
        } catch (PlanningFlowScheduleService.RunFailure failure) {
            return ApiResponse.success(planningSchedule.recordFailure(failure));
        }
    }

    @GetMapping("/{kind}")
    public ApiResponse<Version> read(@PathVariable String kind,
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requirePermission(kind, role);
        var results = jdbc.query("""
                SELECT revision, draft FROM operation_flow_runtime WHERE tenant_id = ? AND flow_kind = ?
                """, (rs, index) -> new Version(rs.getInt("revision"),
                json.readValue(rs.getString("draft"), Draft.class)), tenant, kind);
        return ApiResponse.success(results.isEmpty() ? null : results.getFirst());
    }

    @PutMapping("/{kind}")
    public ApiResponse<Version> save(@PathVariable String kind, @Valid @RequestBody Version input,
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requirePermission(kind, role);
        List<String> path = pathFor(kind, input.draft());
        validateDraft(input.draft(), path);
        if (input.draft().pickupLocationId() != null && (!path.contains("select-orders")
                || !planningSchedule.isActivePickup(tenant, input.draft().pickupLocationId()))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        String data = json.writeValueAsString(input.draft());
        int changed = input.revision() == 0 ? jdbc.update("""
                INSERT INTO operation_flow_runtime (tenant_id, flow_kind, draft, revision)
                VALUES (?, ?, CAST(? AS jsonb), 1) ON CONFLICT DO NOTHING
                """, tenant, kind, data) : jdbc.update("""
                UPDATE operation_flow_runtime SET draft = CAST(? AS jsonb), revision = revision + 1
                WHERE tenant_id = ? AND flow_kind = ? AND revision = ?
                """, data, tenant, kind, input.revision());
        if (changed == 0) throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        return ApiResponse.success(new Version(input.revision() + 1, input.draft()));
    }

    private List<String> pathFor(String kind, Draft draft) {
        return switch (kind) {
            case "import" -> List.of("schedule", "prepare", "import");
            case "planning" -> draft.nodes().stream().anyMatch(node -> "select-orders".equals(node.id()))
                    ? List.of("schedule", "select-orders", "plan") : List.of("schedule", "plan");
            default -> throw new AppException(ErrorCode.VALIDATION_ERROR);
        };
    }

    private void requirePermission(String kind, String role) {
        if (!"import".equals(kind) && !"planning".equals(kind))
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        String permission = "import".equals(kind) ? "order:create" : "dispatch:execute";
        if (!TenantRequestInterceptor.isAuthorizedForPermission(role, permission, Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateDraft(Draft draft, List<String> path) {
        var ids = new java.util.HashSet<String>();
        for (Step node : draft.nodes()) {
            if (!node.id().equals(node.step()) || !path.contains(node.id()) || !ids.add(node.id())
                    || !Double.isFinite(node.position().x()) || !Double.isFinite(node.position().y())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }
        var sources = new java.util.HashSet<String>();
        for (Link edge : draft.edges()) {
            int start = path.indexOf(edge.source());
            if (start < 0 || start + 1 >= path.size() || !path.get(start + 1).equals(edge.target())
                    || !ids.contains(edge.source()) || !ids.contains(edge.target()) || !sources.add(edge.source())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }
}
