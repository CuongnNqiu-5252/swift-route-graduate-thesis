package com.swiftroute.authservice.event;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftroute.authservice.config.TenantEventConfig;
import com.swiftroute.authservice.service.TenantMembershipService;

import lombok.RequiredArgsConstructor;

/** Consumes tenant.created and tenant.approved messages from Order Service. */
@Component
@RequiredArgsConstructor
public class TenantEventListener {
    private final TenantMembershipService tenantMembershipService;

    @RabbitListener(queues = TenantEventConfig.TENANT_EVENT_QUEUE)
    public void handleTenantEvent(TenantEvent event) {
        tenantMembershipService.applyTenantEvent(event);
    }
}
