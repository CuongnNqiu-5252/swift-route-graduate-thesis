package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.dto.request.RegisterTenantRequest;
import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.DeliveryMode;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantWorkflowServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final OutboxEventRepository events = mock(OutboxEventRepository.class);
    private final OperationalReadinessService readiness = mock(OperationalReadinessService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantWorkflowService service = new TenantWorkflowService(tenants, events,
            mock(AuditLogRepository.class), readiness, jdbc, new ObjectMapper());
    private final UUID tenantId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final Tenant tenant = Tenant.builder().id(tenantId).ownerUserId(owner).businessName("Own fleet")
            .address("12 Example Street").contactPhone("0900000000").countryCode("VN").version(0L)
            .optimizationWeights(new OptimizationWeights(0.65, 0.35))
            .build();

    @BeforeEach
    void setUp() {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(readiness.hasPickup(tenant)).thenReturn(true);
    }

    @Test
    void newWorkspacesUseCommonDispatchDefaultsEvenWithALegacyBusinessType() {
        when(tenants.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant created = invocation.getArgument(0);
            created.setId(UUID.randomUUID());
            return created;
        });
        var identity = Map.<String, Object>of("email_verified", true, "user_id", owner, "email", "owner@example.test");
        Tenant general = service.create(new RegisterTenantRequest(
                "General Workspace", null, "0900000000", "12 Example Street", "VN"), identity, "general");
        Tenant legacy = service.create(new RegisterTenantRequest(
                "Legacy Workspace", BusinessType.PHARMACY, "0900000000", "12 Example Street", "VN"), identity, "legacy");

        assertThat(general.getBusinessType()).isEqualTo(BusinessType.OTHER);
        assertThat(legacy.getBusinessType()).isEqualTo(BusinessType.PHARMACY);
        assertThat(general.getOptimizationWeights()).isEqualTo(new OptimizationWeights(0.5, 0.5));
        assertThat(general.getDispatchConfig().getMaxOrdersPerDriver()).isEqualTo(5);
        assertThat(general.getDispatchConfig().getMaxWaitTimeSeconds()).isEqualTo(300);
        assertThat(legacy.getOptimizationWeights()).isEqualTo(general.getOptimizationWeights());
        assertThat(legacy.getDispatchConfig()).isEqualTo(general.getDispatchConfig());
    }

    @Test
    void ownFleetCanSubmitAfterProfileAndPickup() {
        when(jdbc.queryForObject(contains("SELECT count(*) FROM tenant_verification_requests"), eq(Integer.class),
                eq(tenantId))).thenReturn(0);
        when(jdbc.queryForList(contains("WHERE request_id=?"), any(UUID.class)))
                .thenReturn(List.of(Map.of("status", "SUBMITTED")));

        assertThat(service.onboarding(tenantId)).containsEntry("completed_steps", List.of("BUSINESS_PROFILE", "PICKUP_POINT"))
                .containsEntry("next_action", "VERIFICATION_SUBMISSION");
        assertThat(service.submit(tenantId, owner, "submission")).containsEntry("status", "SUBMITTED");
        verify(events).save(any());
    }

    @Test
    void poolOnlyProfileMustSwitchModeBeforeSubmitting() {
        tenant.setDeliveryMode(DeliveryMode.SHARED_POOL);
        when(jdbc.queryForObject(contains("SELECT count(*) FROM tenant_verification_requests"), eq(Integer.class),
                eq(tenantId))).thenReturn(0);

        assertThat(service.onboarding(tenantId)).containsEntry("next_action", "DELIVERY_MODE");
        assertThatThrownBy(() -> service.submit(tenantId, owner, "submission"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_INCOMPLETE);
    }

    @Test
    void legacyApprovalPreferenceIsIgnoredWithoutChangingRoutingWeights() {
        when(jdbc.queryForObject(contains("SELECT count(*) FROM tenant_verification_requests"), eq(Integer.class),
                eq(tenantId))).thenReturn(0);

        service.configure(tenantId, new TenantWorkflowService.Setup(null, null, null, null, null, null,
                Map.of("preparation_enabled", true, "preparation_minutes", 15, "routing_approval_required", true)));

        assertThat(tenant.getDeliverySettings()).containsEntry("preparation_minutes", 15);
        assertThat(tenant.getOptimizationWeights().getTimeWeight()).isEqualTo(0.65);
        assertThat(tenant.getDeliverySettings()).doesNotContainKey("routing_approval_required");
    }
}
