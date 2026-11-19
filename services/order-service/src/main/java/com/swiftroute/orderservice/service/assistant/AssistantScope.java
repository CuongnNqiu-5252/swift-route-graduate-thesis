package com.swiftroute.orderservice.service.assistant;

import java.util.UUID;

public record AssistantScope(UUID tenantId, UUID userId, String role, boolean readOrders, boolean readPlans,
        UUID selectedPlanId) {}
