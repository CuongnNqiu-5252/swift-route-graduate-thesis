package com.swiftroute.orderservice.service;

import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.swiftroute.orderservice.repository.TenantRepository;
import com.swiftroute.orderservice.security.TenantIdentityClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {
    private final TenantProvisioningStore store;
    private final TenantIdentityClient identities;
    private final TenantRepository tenants;

    public TenantProvisioningStore.State provision(UUID id) {
        if(store.claim(id)) {
            try {
                var tenant=tenants.findById(id).orElseThrow();
                identities.provision(id,tenant.getOwnerUserId());
                store.ready(id);
            } catch(RuntimeException ex) {
                store.failed(id);
                log.warn("Owner provisioning deferred for tenant {}",id);
            }
        }
        return store.state(id);
    }
    @Scheduled(fixedDelayString="${tenant.provisioning.retry-delay-ms:30000}")
    public void recover() {
        for(UUID id:store.due()) {
            try { provision(id); }
            catch(RuntimeException ex) { log.warn("Workspace recovery deferred for tenant {}",id); }
        }
    }
}
