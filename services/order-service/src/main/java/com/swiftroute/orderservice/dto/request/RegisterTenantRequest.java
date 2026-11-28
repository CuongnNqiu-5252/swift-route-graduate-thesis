package com.swiftroute.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import jakarta.validation.constraints.*;

public record RegisterTenantRequest(
    @JsonAlias("businessName") @NotBlank @Size(min=2,max=255) String businessName,
    @JsonAlias("businessType") BusinessType businessType,
    @JsonAlias("contactPhone") @NotBlank @Size(min=9,max=20) @Pattern(regexp="[+0-9 ()-]{9,20}") String contactPhone,
    @JsonAlias("countryCode") @NotBlank @Pattern(regexp="[A-Z]{2}") String countryCode) {
    public RegisterTenantRequest {
        if (businessType == null) {
            businessType = BusinessType.OTHER;
        }
    }

    public RegisterTenantRequest(
            String businessName,
            BusinessType businessType,
            String contactPhone,
            String ignoredAddress,
            String countryCode) {
        this(businessName, businessType, contactPhone, countryCode);
    }
}
