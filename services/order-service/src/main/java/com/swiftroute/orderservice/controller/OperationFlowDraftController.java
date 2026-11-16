package com.swiftroute.orderservice.controller;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/orders/operation-flow/drafts")
@RequiredArgsConstructor
public class OperationFlowDraftController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Position(double x, double y) {}
    public record Step(@NotNull String id, @NotNull String step, @NotNull @Valid Position position) {}
    public record Link(@NotNull @Size(max = 100) String id, @NotNull String source, @NotNull String target) {}
    public record Schedule(
            @NotNull @Pattern(regexp = "daily|interval") String mode,
            @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String time,
            @Min(1) @Max(168) int hours) {}
    public record Draft(
            @NotNull @Size(max = 3) List<@NotNull @Valid Step> nodes,
            @NotNull @Size(max = 2) List<@NotNull @Valid Link> edges,
            @NotNull @Valid Schedule schedule, boolean resolveCoordinates, boolean stopOnCsvErrors) {}
    public record Version(@Min(0) int revision, @NotNull @Valid Draft draft) {}

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
        List<String> path = requirePermission(kind, role);
        validateDraft(input.draft(), path);
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

    private List<String> requirePermission(String kind, String role) {
        List<String> path = switch (kind) {
            case "import" -> List.of("schedule", "prepare", "import");
            case "planning" -> List.of("schedule", "plan");
            default -> throw new AppException(ErrorCode.VALIDATION_ERROR);
        };
        String permission = "import".equals(kind) ? "order:create" : "dispatch:execute";
        if (!TenantRequestInterceptor.isAuthorizedForPermission(role, permission, Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return path;
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
