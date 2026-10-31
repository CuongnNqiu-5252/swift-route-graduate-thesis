package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.dto.request.CreateTenantRequest;
import com.swiftroute.orderservice.dto.request.TenantStatusRequest;
import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, outboxEventRepository);
    }

    @Test
    void createTenantCreatesPendingTenantAndDurableEvent() {
        UUID ownerId = UUID.randomUUID();
        when(tenantRepository.existsByBusinessNameIgnoreCase("Swift Cafe")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });

        var response = tenantService.createTenant(
                new CreateTenantRequest(" Swift Cafe ", BusinessType.CAFE, "Siam Square", 13.74, 100.53,
                        new OptimizationWeights(0.6, 0.4)),
                ownerId, "OWNER");

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(response.ownerUserId()).isEqualTo(ownerId);
        assertThat(response.status()).isEqualTo(TenantStatus.PENDING);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("tenant.created");
    }

    @Test
    void rejectedTenantCannotBeApprovedAgain() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).status(TenantStatus.REJECTED).build();
        when(tenantRepository.findById(tenantId)).thenReturn(java.util.Optional.of(tenant));

        assertThatThrownBy(() -> tenantService.changeStatus(tenantId,
                new TenantStatusRequest(TenantStatus.APPROVED, "Reviewed"), UUID.randomUUID(), "ADMIN"))
                .isInstanceOf(AppException.class);
    }
}
