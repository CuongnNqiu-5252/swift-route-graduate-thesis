package com.swiftroute.orderservice.service;

import com.swiftroute.orderservice.dto.response.PageRequests;
import com.swiftroute.orderservice.dto.response.PageResponse;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PickupLocationService {

    private final PickupLocationRepository pickupLocationRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public PageResponse<PickupLocationResponse> getPickupLocationPage(UUID tenantId, PickupLocationStatus status, int page, int size) {
        return PageResponse.fromPage(pickupLocationRepository.findPage(tenantId, status,
                PageRequests.newestFirst(page, size, "createdAt")).map(PickupLocationResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<PickupLocationResponse> getPickupLocations(UUID tenantId, PickupLocationStatus status) {
        List<PickupLocation> locations = (status != null)
                ? pickupLocationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : pickupLocationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        return locations.stream().map(PickupLocationResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public PickupLocationResponse getPickupLocation(UUID tenantId, UUID id) {
        PickupLocation location = pickupLocationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.PICKUP_LOCATION_NOT_FOUND));
        return PickupLocationResponse.fromEntity(location);
    }

    @Transactional
    public PickupLocationResponse createPickupLocation(UUID tenantId, CreatePickupLocationRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        long existingCount = pickupLocationRepository.countByTenantId(tenantId);
        boolean shouldBeDefault = (existingCount == 0) || Boolean.TRUE.equals(request.isDefault());

        if (shouldBeDefault && existingCount > 0) {
            pickupLocationRepository.clearAllDefaults(tenantId);
        }

        PickupLocation location = PickupLocation.builder()
                .tenant(tenant)
                .name(request.name().trim())
                .address(request.address().trim())
                .lat(request.lat())
                .lng(request.lng())
                .contactName(request.contactName() != null ? request.contactName().trim() : null)
                .contactPhone(request.contactPhone() != null ? request.contactPhone().trim() : null)
                .isDefault(shouldBeDefault)
                .status(PickupLocationStatus.ACTIVE)
                .build();

        PickupLocation saved = pickupLocationRepository.save(location);
        log.info("Created pickup location id={} for tenantId={}, isDefault={}", saved.getId(), tenantId, shouldBeDefault);
        return PickupLocationResponse.fromEntity(saved);
    }

    @Transactional
    public PickupLocationResponse updatePickupLocation(UUID tenantId, UUID id, UpdatePickupLocationRequest request) {
        PickupLocation location = pickupLocationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.PICKUP_LOCATION_NOT_FOUND));

        boolean willBeDefault = Boolean.TRUE.equals(request.isDefault());
        if (willBeDefault && !Boolean.TRUE.equals(location.getIsDefault())) {
            pickupLocationRepository.clearOtherDefaults(tenantId, id);
        }

        // Prevent setting a default location to INACTIVE while other active locations exist
        if (request.status() == PickupLocationStatus.INACTIVE && Boolean.TRUE.equals(location.getIsDefault())) {
            long activeCount = pickupLocationRepository.countByTenantIdAndStatus(tenantId, PickupLocationStatus.ACTIVE);
            if (activeCount > 1) {
                throw new AppException(ErrorCode.CANNOT_DELETE_DEFAULT_PICKUP_LOCATION);
            }
            willBeDefault = false;
        }

        location.setName(request.name().trim());
        location.setAddress(request.address().trim());
        location.setLat(request.lat());
        location.setLng(request.lng());
        location.setContactName(request.contactName() != null ? request.contactName().trim() : null);
        location.setContactPhone(request.contactPhone() != null ? request.contactPhone().trim() : null);
        location.setIsDefault(willBeDefault);
        if (request.status() != null) {
            location.setStatus(request.status());
        }

        PickupLocation saved = pickupLocationRepository.save(location);
        log.info("Updated pickup location id={} for tenantId={}", saved.getId(), tenantId);
        return PickupLocationResponse.fromEntity(saved);
    }

    @Transactional
    public PickupLocationResponse setDefaultLocation(UUID tenantId, UUID id) {
        PickupLocation location = pickupLocationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.PICKUP_LOCATION_NOT_FOUND));

        if (location.getStatus() != PickupLocationStatus.ACTIVE) {
            throw new AppException(ErrorCode.PICKUP_LOCATION_INACTIVE);
        }

        pickupLocationRepository.clearOtherDefaults(tenantId, id);
        location.setIsDefault(true);
        PickupLocation saved = pickupLocationRepository.save(location);
        log.info("Set default pickup location id={} for tenantId={}", saved.getId(), tenantId);
        return PickupLocationResponse.fromEntity(saved);
    }

    @Transactional
    public void deletePickupLocation(UUID tenantId, UUID id) {
        PickupLocation location = pickupLocationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.PICKUP_LOCATION_NOT_FOUND));

        if (Boolean.TRUE.equals(location.getIsDefault())) {
            long activeCount = pickupLocationRepository.countByTenantIdAndStatus(tenantId, PickupLocationStatus.ACTIVE);
            if (activeCount > 1) {
                throw new AppException(ErrorCode.CANNOT_DELETE_DEFAULT_PICKUP_LOCATION);
            }
        }

        // Soft delete
        location.setStatus(PickupLocationStatus.INACTIVE);
        location.setIsDefault(false);
        pickupLocationRepository.save(location);
        log.info("Soft-deleted pickup location id={} for tenantId={}", id, tenantId);
    }
}

