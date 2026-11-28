package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.assistant.AssistantScope;
import com.swiftroute.orderservice.service.assistant.OperationsAssistant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/orders/assistant")
@RequiredArgsConstructor
public class OperationsAssistantController {
    private static final Set<String> ROLES = Set.of("OWNER", "TENANT_ADMIN", "OPERATOR");

    private final OperationsAssistant assistant;

    public record Turn(@NotNull @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 4000) String text) {}

    public record Query(@NotBlank @Size(max = 2000) String question,
            @Size(max = 8) List<@NotNull @Valid Turn> history,
            @JsonProperty("plan_id") @JsonAlias("planId") UUID planId,
            @Pattern(regexp = "vi|en") String language,
            @Pattern(regexp = "shift_briefing") String intent) {}

    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> stream(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role,
            @Valid @RequestBody Query query) {
        if (tenantId == null || !ROLES.contains(role.toUpperCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        boolean readOrders = TenantRequestInterceptor.isAuthorizedForPermission(role, "order:read", ROLES);
        boolean executeDispatch = TenantRequestInterceptor.isAuthorizedForPermission(role, "dispatch:execute", ROLES);
        boolean readPlans = executeDispatch || TenantRequestInterceptor.isAuthorizedForPermission(role, "dispatch:read", ROLES);
        if (!readOrders && !readPlans) throw new AppException(ErrorCode.ACCESS_DENIED);

        boolean createOrders = TenantRequestInterceptor.isAuthorizedForPermission(role, "order:create", ROLES);
        var scope = new AssistantScope(tenantId, userId, role, readOrders, readPlans, query.planId(), executeDispatch, createOrders);
        var history = query.history() == null ? List.<OperationsAssistant.Turn>of()
                : query.history().stream().map(turn -> new OperationsAssistant.Turn(turn.role(), turn.text())).toList();
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(assistant.stream(history, query.question(), "en".equals(query.language()), scope,
                        "shift_briefing".equals(query.intent())));
    }
}
