package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.service.BusinessTypeProfileRegistry.BusinessTypeProfile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class BusinessTypeProfileRegistryTest {

    private BusinessTypeProfileRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BusinessTypeProfileRegistry();
    }

    @Test
    @DisplayName("Should contain profiles for all 5 business types including OTHER")
    void containsAllProfiles() {
        Map<BusinessType, BusinessTypeProfile> profiles = registry.getAllProfiles();
        assertThat(profiles).containsKeys(
                BusinessType.RESTAURANT,
                BusinessType.CAFE,
                BusinessType.PHARMACY,
                BusinessType.RETAIL,
                BusinessType.OTHER
        );
    }

    @Test
    @DisplayName("Should return accurate preset values for RESTAURANT")
    void restaurantProfileValues() {
        BusinessTypeProfile profile = registry.getProfile(BusinessType.RESTAURANT);
        assertThat(profile.timeWeight()).isEqualTo(0.8);
        assertThat(profile.costWeight()).isEqualTo(0.2);
        assertThat(profile.maxOrdersPerDriver()).isEqualTo(3);
        assertThat(profile.maxWaitTimeSeconds()).isEqualTo(180);
        assertThat(profile.urgentOrderMode()).isEqualTo("IMMEDIATE_SINGLE");
        assertThat(profile.allowPoolDrivers()).isTrue();
    }

    @Test
    @DisplayName("Should return accurate preset values for RETAIL")
    void retailProfileValues() {
        BusinessTypeProfile profile = registry.getProfile(BusinessType.RETAIL);
        assertThat(profile.timeWeight()).isEqualTo(0.4);
        assertThat(profile.costWeight()).isEqualTo(0.6);
        assertThat(profile.maxOrdersPerDriver()).isEqualTo(8);
        assertThat(profile.maxWaitTimeSeconds()).isEqualTo(600);
        assertThat(profile.urgentOrderMode()).isEqualTo("BATCH_IMMEDIATE");
        assertThat(profile.allowPoolDrivers()).isFalse();
    }

    @Test
    @DisplayName("Should return defensive copies preventing mutation of global preset")
    void defensiveCopiesPreventMutation() {
        BusinessTypeProfile profile = registry.getProfile(BusinessType.RESTAURANT);
        TenantDispatchConfig config1 = profile.toDispatchConfig();
        config1.setMaxOrdersPerDriver(99); // Mutate copy

        TenantDispatchConfig config2 = profile.toDispatchConfig();
        assertThat(config2.getMaxOrdersPerDriver()).isEqualTo(3); // Global preset intact!
    }

    @Test
    @DisplayName("Applying profile updates settings while preserving unrelated custom fields")
    void applyProfilePreservesCustomFields() {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .businessName("Nha thuoc Hoang Gia")
                .businessType(BusinessType.OTHER)
                .status(TenantStatus.APPROVED)
                .dispatchConfig(TenantDispatchConfig.builder()
                        .requireApproval(true) // Custom preference
                        .algorithm("held_karp") // Custom preference
                        .build())
                .build();

        registry.applyProfile(tenant, BusinessType.PHARMACY);

        assertThat(tenant.getBusinessType()).isEqualTo(BusinessType.PHARMACY);
        assertThat(tenant.getOptimizationWeights().getTimeWeight()).isEqualTo(0.9);
        assertThat(tenant.getOptimizationWeights().getCostWeight()).isEqualTo(0.1);
        assertThat(tenant.getDispatchConfig().getMaxOrdersPerDriver()).isEqualTo(2);
        assertThat(tenant.getDispatchConfig().getMaxWaitTimeSeconds()).isEqualTo(120);
        assertThat(tenant.getDispatchConfig().getAllowPoolDrivers()).isTrue();

        // Preserved custom preferences
        assertThat(tenant.getDispatchConfig().getRequireApproval()).isTrue();
        assertThat(tenant.getDispatchConfig().getAlgorithm()).isEqualTo("held_karp");
    }

    @Test
    @DisplayName("Every business preset produces a valid TenantDispatchConfig with HH:mm scheduled dispatch time")
    void allPresetsProduceValidDispatchConfigs() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        for (BusinessTypeProfile profile : registry.getAllProfiles().values()) {
            TenantDispatchConfig config = profile.toDispatchConfig();
            Set<ConstraintViolation<TenantDispatchConfig>> violations = validator.validate(config);
            assertThat(violations)
                    .as("Preset %s dispatch config violations", profile.businessType())
                    .isEmpty();
            assertThat(config.getScheduledDispatchEnabled()).isNotNull();
            assertThat(config.getScheduledDispatchTime()).matches("^([01]\\d|2[0-3]):[0-5]\\d$");
        }
    }
}

