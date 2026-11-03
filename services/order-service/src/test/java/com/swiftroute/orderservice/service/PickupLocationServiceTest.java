package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.dto.request.CreatePickupLocationRequest;
import com.swiftroute.orderservice.dto.request.UpdatePickupLocationRequest;
import com.swiftroute.orderservice.dto.response.PickupLocationResponse;
import com.swiftroute.orderservice.entity.PickupLocation;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.PickupLocationStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.PickupLocationRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class PickupLocationServiceTest {

    @Mock
    private PickupLocationRepository pickupLocationRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private PickupLocationService pickupLocationService;

    private UUID tenantId;
    private Tenant tenant;
    private PickupLocation sampleLocation;
    private UUID locationId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .businessName("Test Tenant")
                .build();

        locationId = UUID.randomUUID();
        sampleLocation = PickupLocation.builder()
                .id(locationId)
                .tenant(tenant)
                .name("Kho Can Tho")
                .address("123 Duong 30/4, Ninh Kieu, Can Tho")
                .lat(10.03)
                .lng(105.78)
                .contactName("Nguyen Van A")
                .contactPhone("0901234567")
                .isDefault(true)
                .status(PickupLocationStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should return pickup locations for tenant")
    void getPickupLocationsSuccess() {
        when(pickupLocationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(sampleLocation));

        List<PickupLocationResponse> result = pickupLocationService.getPickupLocations(tenantId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Kho Can Tho");
        assertThat(result.get(0).isDefault()).isTrue();
    }

    @Test
    @DisplayName("Should automatically set first location as default when tenant has no prior locations")
    void createFirstLocationSetsDefault() {
        CreatePickupLocationRequest req = new CreatePickupLocationRequest(
                "Kho Binh Thuy", "456 CMT8, Can Tho", 10.05, 105.75, "Tran Van B", "0912345678", false
        );

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(pickupLocationRepository.countByTenantId(tenantId)).thenReturn(0L);
        when(pickupLocationRepository.save(any(PickupLocation.class))).thenAnswer(inv -> {
            PickupLocation p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PickupLocationResponse res = pickupLocationService.createPickupLocation(tenantId, req);

        assertThat(res.name()).isEqualTo("Kho Binh Thuy");
        assertThat(res.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Should clear other defaults when new location is marked as default")
    void createLocationWithDefaultClearsOthers() {
        CreatePickupLocationRequest req = new CreatePickupLocationRequest(
                "Kho Moi", "789 Hung Vuong", 10.04, 105.77, null, null, true
        );

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(pickupLocationRepository.countByTenantId(tenantId)).thenReturn(1L);
        when(pickupLocationRepository.save(any(PickupLocation.class))).thenAnswer(inv -> {
            PickupLocation p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PickupLocationResponse res = pickupLocationService.createPickupLocation(tenantId, req);

        verify(pickupLocationRepository).clearAllDefaults(tenantId);
        assertThat(res.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Should set default location and clear others")
    void setDefaultLocationSuccess() {
        when(pickupLocationRepository.findByIdAndTenantId(locationId, tenantId))
                .thenReturn(Optional.of(sampleLocation));
        when(pickupLocationRepository.save(any(PickupLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        PickupLocationResponse res = pickupLocationService.setDefaultLocation(tenantId, locationId);

        verify(pickupLocationRepository).clearOtherDefaults(tenantId, locationId);
        assertThat(res.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Should soft delete pickup location")
    void deletePickupLocationSuccess() {
        sampleLocation.setIsDefault(false);
        when(pickupLocationRepository.findByIdAndTenantId(locationId, tenantId))
                .thenReturn(Optional.of(sampleLocation));
        when(pickupLocationRepository.save(any(PickupLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        pickupLocationService.deletePickupLocation(tenantId, locationId);

        assertThat(sampleLocation.getStatus()).isEqualTo(PickupLocationStatus.INACTIVE);
        assertThat(sampleLocation.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("Should reject deleting default location if multiple active locations exist")
    void rejectDeletingDefaultWhenMultipleActiveLocations() {
        when(pickupLocationRepository.findByIdAndTenantId(locationId, tenantId))
                .thenReturn(Optional.of(sampleLocation));
        when(pickupLocationRepository.countByTenantIdAndStatus(tenantId, PickupLocationStatus.ACTIVE))
                .thenReturn(2L);

        assertThatThrownBy(() -> pickupLocationService.deletePickupLocation(tenantId, locationId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_DELETE_DEFAULT_PICKUP_LOCATION);
    }
}

