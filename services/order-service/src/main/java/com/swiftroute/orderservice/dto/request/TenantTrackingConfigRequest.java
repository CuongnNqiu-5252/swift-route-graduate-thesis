package com.swiftroute.orderservice.dto.request;

import org.jetbrains.annotations.NotNull;

import com.swiftroute.orderservice.entity.enums.TrackingEmailTrigger;

import jakarta.validation.constraints.Size;

public record TenantTrackingConfigRequest(

        @NotNull Boolean discloseEta,

        @NotNull TrackingEmailTrigger emailTrigger,

        @Size(max = 255) String emailSenderName,

        @Size(max = 500) String emailSubject,

        String emailBody

) {
}
