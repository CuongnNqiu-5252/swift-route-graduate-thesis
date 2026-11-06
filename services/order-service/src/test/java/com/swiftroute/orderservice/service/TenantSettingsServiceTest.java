package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.dto.request.UpdateTenantSettingsRequest;
import com.swiftroute.orderservice.dto.response.TenantSettingsResponse;
import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class TenantSettingsServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private BusinessTypeProfileRegistry profileRegistry;
    private TenantSettingsService settingsService;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        profileRegistry = new BusinessTypeProfileRegistry();
        settingsService = new TenantSettingsService(tenantRepository, profileRegistry);

        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .businessName("Quan Nuoc Ninh Kieu")
                .businessType(BusinessType.CAFE)
                .status(TenantStatus.APPROVED)
                .optimizationWeights(new OptimizationWeights(0.7, 0.3))
                .dispatchConfig(TenantDispatchConfig.builder().maxOrdersPerDriver(4).build())
                .build();
    }

    @Test
    @DisplayName("Should successfully retrieve tenant settings")
    void getSettingsSuccess() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantSettingsResponse response = settingsService.getSettings(tenantId);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.businessName()).isEqualTo("Quan Nuoc Ninh Kieu");
        assertThat(response.businessType()).isEqualTo(BusinessType.CAFE);
    }

    @Test
    @DisplayName("Should update settings with valid weights that sum to 1.0")
    void updateSettingsValidWeightsSuccess() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantSettingsRequest request = new UpdateTenantSettingsRequest(
                "Quan Nuoc Ninh Kieu Updated",
                BusinessType.CAFE,
                "123 Nguyen Trai",
                TenantDispatchConfig.builder().maxOrdersPerDriver(5).allowPoolDrivers(true).build(),
                new OptimizationWeights(0.6, 0.4)
        );

        TenantSettingsResponse response = settingsService.updateSettings(tenantId, request);
        assertThat(response.businessName()).isEqualTo("Quan Nuoc Ninh Kieu Updated");
        assertThat(response.optimizationWeights().getTimeWeight()).isEqualTo(0.6);
        assertThat(response.optimizationWeights().getCostWeight()).isEqualTo(0.4);
        assertThat(response.dispatchConfig().getAllowPoolDrivers()).isTrue();
    }

    @Test
    @DisplayName("Should reject settings update when weights do not sum to 1.0")
    void updateSettingsInvalidWeightsSumThrowsException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        UpdateTenantSettingsRequest request = new UpdateTenantSettingsRequest(
                null, null, null, null,
                new OptimizationWeights(0.8, 0.8) // Sum = 1.6 != 1.0
        );

        assertThatThrownBy(() -> settingsService.updateSettings(tenantId, request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("Should apply preset profile for RETAIL")
    void applyPresetRetailSuccess() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantSettingsResponse response = settingsService.applyPreset(tenantId, BusinessType.RETAIL);

        assertThat(response.businessType()).isEqualTo(BusinessType.RETAIL);
        assertThat(response.optimizationWeights().getTimeWeight()).isEqualTo(0.4);
        assertThat(response.optimizationWeights().getCostWeight()).isEqualTo(0.6);
        assertThat(response.dispatchConfig().getMaxOrdersPerDriver()).isEqualTo(8);
        assertThat(response.dispatchConfig().getMaxWaitTimeSeconds()).isEqualTo(600);
        assertThat(response.dispatchConfig().getAllowPoolDrivers()).isFalse();
    }
}

