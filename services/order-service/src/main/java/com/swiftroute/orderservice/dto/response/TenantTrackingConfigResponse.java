package com.swiftroute.orderservice.dto.response;

import com.swiftroute.orderservice.entity.enums.TrackingEmailTrigger;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TenantTrackingConfigResponse {
    private boolean discloseEta;
    private TrackingEmailTrigger emailTrigger;
    private String emailSenderName;
    private String emailSubject;
    private String emailBody;
}
