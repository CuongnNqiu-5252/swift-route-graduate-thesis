package com.swiftroute.orderservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.UpdateTenantSettingsRequest;
import com.swiftroute.orderservice.dto.response.TenantSettingsResponse;
import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSettingsService {

    private final TenantRepository tenantRepository;
    private final BusinessTypeProfileRegistry profileRegistry;

    @Transactional(readOnly = true)
    public TenantSettingsResponse getSettings(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        return TenantSettingsResponse.fromEntity(tenant);
    }

    @Transactional
    public TenantSettingsResponse updateSettings(UUID tenantId, UpdateTenantSettingsRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        if (request.businessName() != null && !request.businessName().isBlank()) {
            tenant.setBusinessName(request.businessName().trim());
        }

        if (request.businessType() != null) {
            tenant.setBusinessType(request.businessType());
        }

        if (request.address() != null) {
            tenant.setAddress(request.address().trim());
        }

        if (request.optimizationWeights() != null) {
            validateOptimizationWeights(request.optimizationWeights());
            tenant.setOptimizationWeights(request.optimizationWeights());
        }

        if (request.dispatchConfig() != null) {
            validateDispatchConfig(request.dispatchConfig());
            tenant.setDispatchConfig(request.dispatchConfig());
        }

        Tenant saved = tenantRepository.save(tenant);
        log.info("Updated tenant settings for tenantId={}, businessName='{}'", tenantId, saved.getBusinessName());
        return TenantSettingsResponse.fromEntity(saved);
    }

    @Transactional
    public TenantSettingsResponse applyPreset(UUID tenantId, BusinessType type) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        profileRegistry.applyProfile(tenant, type);
        Tenant saved = tenantRepository.save(tenant);
        log.info("Applied preset {} to tenantId={}, businessName='{}'", type, tenantId, saved.getBusinessName());
        return TenantSettingsResponse.fromEntity(saved);
    }

    private void validateOptimizationWeights(OptimizationWeights weights) {
        if (weights.getTimeWeight() == null || weights.getCostWeight() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        if (weights.getTimeWeight() < 0.0 || weights.getTimeWeight() > 1.0 ||
                weights.getCostWeight() < 0.0 || weights.getCostWeight() > 1.0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        // Time weight + cost weight must sum to 1.0 within reasonable decimal tolerance
        double sum = weights.getTimeWeight() + weights.getCostWeight();
        if (Math.abs(sum - 1.0) > 0.005) {
            log.warn("Invalid optimization weights: time={}, cost={}, sum={}",
                    weights.getTimeWeight(), weights.getCostWeight(), sum);
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateDispatchConfig(TenantDispatchConfig config) {
        if (config.getMaxOrdersPerDriver() != null
                && (config.getMaxOrdersPerDriver() < 1 || config.getMaxOrdersPerDriver() > 20)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (config.getMaxWaitTimeSeconds() != null
                && (config.getMaxWaitTimeSeconds() < 30 || config.getMaxWaitTimeSeconds() > 3600)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (config.getUrgentOrderMode() != null
                && !List.of("IMMEDIATE_SINGLE", "BATCH_IMMEDIATE").contains(config.getUrgentOrderMode())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (config.getAlgorithm() != null
                && !List.of("auto", "held_karp", "nearest_neighbor", "genetic").contains(config.getAlgorithm())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (config.getScheduledDispatchTime() != null) {
            if (!config.getScheduledDispatchTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }
}
