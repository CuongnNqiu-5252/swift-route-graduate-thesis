package com.swiftroute.orderservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.DeliveryProofConfigRequest;
import com.swiftroute.orderservice.dto.request.UpdateTenantSettingsRequest;
import com.swiftroute.orderservice.dto.response.TenantSettingsResponse;
import com.swiftroute.orderservice.entity.DeliveryProofConfig;
import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.ProofPolicy;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryProofConfigRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TenantSettingsService {

    private final TenantRepository tenantRepository;
    private final DeliveryProofConfigRepository deliveryProofConfigRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public TenantSettingsService(
            TenantRepository tenantRepository,
            DeliveryProofConfigRepository deliveryProofConfigRepository) {
        this.tenantRepository = tenantRepository;
        this.deliveryProofConfigRepository = deliveryProofConfigRepository;
    }

    public TenantSettingsService(TenantRepository tenantRepository) {
        this(tenantRepository, null);
    }

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
        if (config.getScheduledDispatchTime() != null) {
            if (!config.getScheduledDispatchTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    @Transactional(readOnly = true)
    public DeliveryProofConfigRequest getConfig(UUID tenantId) {
        return deliveryProofConfigRepository.findByTenantId(tenantId)
                .map(this::toDto)
                .orElseGet(this::defaultConfig);
    }

    @Transactional
    public DeliveryProofConfigRequest updateConfig(
            UUID tenantId,
            DeliveryProofConfigRequest request) {

        DeliveryProofConfig config = deliveryProofConfigRepository
                .findByTenantId(tenantId)
                .orElseGet(() -> {
                    DeliveryProofConfig newConfig = new DeliveryProofConfig();
                    newConfig.setTenantId(tenantId);
                    return newConfig;
                });

        config.setSuccessNotePolicy(request.successNotePolicy());
        config.setSuccessSignaturePolicy(request.successSignaturePolicy());
        config.setSuccessPhotoPolicy(request.successPhotoPolicy());

        config.setFailedNotePolicy(request.failedNotePolicy());
        config.setFailedSignaturePolicy(request.failedSignaturePolicy());
        config.setFailedPhotoPolicy(request.failedPhotoPolicy());

        return toDto(deliveryProofConfigRepository.save(config));
    }

    private DeliveryProofConfigRequest toDto(
            DeliveryProofConfig config) {

        return new DeliveryProofConfigRequest(
                config.getSuccessNotePolicy(),
                config.getSuccessSignaturePolicy(),
                config.getSuccessPhotoPolicy(),
                config.getFailedNotePolicy(),
                config.getFailedSignaturePolicy(),
                config.getFailedPhotoPolicy());
    }

    private DeliveryProofConfigRequest defaultConfig() {
        return new DeliveryProofConfigRequest(
                ProofPolicy.OPTIONAL,
                ProofPolicy.DISABLED,
                ProofPolicy.REQUIRED,

                ProofPolicy.REQUIRED,
                ProofPolicy.DISABLED,
                ProofPolicy.OPTIONAL);
    }

}
