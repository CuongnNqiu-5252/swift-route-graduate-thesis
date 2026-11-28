package com.swiftroute.orderservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.TenantTrackingConfigRequest;
import com.swiftroute.orderservice.dto.response.TenantTrackingConfigResponse;
import com.swiftroute.orderservice.entity.TenantTrackingConfig;
import com.swiftroute.orderservice.entity.enums.TrackingEmailTrigger;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.TenantTrackingConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantTrackingConfigService {
    private final TenantTrackingConfigRepository tenantTrackingConfigRepository;

    @Transactional(readOnly = true)
    public TenantTrackingConfigResponse getConfig(UUID tenantId) {
        TenantTrackingConfig t = tenantTrackingConfigRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        return TenantTrackingConfigResponse.builder()
                .discloseEta(t.getDiscloseEta())
                .emailTrigger(t.getEmailTrigger())
                .emailBody(t.getEmailBody())
                .emailSenderName(t.getEmailSenderName())
                .emailSubject(t.getEmailSubject())
                .build();
    }

    @Transactional
    public TenantTrackingConfigResponse updateConfig(
            UUID tenantId,
            TenantTrackingConfigRequest request) {
        TenantTrackingConfig config = tenantTrackingConfigRepository
                .findById(tenantId)
                .orElseGet(() -> defaultConfig(tenantId));
        config = tenantTrackingConfigRepository
                .save(TenantTrackingConfig.builder()
                        .discloseEta(request.discloseEta())
                        .emailTrigger(request.emailTrigger())
                        .emailSenderName(request.emailSenderName())
                        .emailSubject(request.emailSubject())
                        .emailBody(request.emailBody())
                        .build());
        return TenantTrackingConfigResponse.builder()
                .discloseEta(config.getDiscloseEta())
                .emailTrigger(config.getEmailTrigger())
                .emailSenderName(config.getEmailSenderName())
                .emailSubject(config.getEmailSubject())
                .emailBody(config.getEmailBody())
                .build();
    }

    private TenantTrackingConfig defaultConfig(UUID tenantId) {
        TenantTrackingConfig config = TenantTrackingConfig.builder()
                .tenantId(tenantId)
                .discloseEta(true)
                .emailSubject("Track your order {ORDER_KEY}")
                .emailTrigger(TrackingEmailTrigger.NONE)
                .emailSenderName("SwiftRoute")
                .emailBody("""
                        <p>Dear {CONTACT_NAME},</p>
                        <p>You can track your order at:</p>
                        <p>{PUBLIC_TRACKING}</p>
                        <p>Thank you!</p>
                        """)
                .build();
        return config;
    }
}
