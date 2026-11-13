package com.swiftroute.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import jakarta.validation.constraints.*;

public record RegisterTenantRequest(
    @JsonAlias("businessName") @NotBlank @Size(min=2,max=255) String businessName,
    @JsonAlias("businessType") @NotNull BusinessType businessType,
    @JsonAlias("contactPhone") @NotBlank @Size(min=9,max=20) @Pattern(regexp="[+0-9 ()-]{9,20}") String contactPhone,
    @NotBlank @Size(min=5,max=500) String address,
    @JsonAlias("countryCode") @NotBlank @Pattern(regexp="[A-Z]{2}") String countryCode) {}
