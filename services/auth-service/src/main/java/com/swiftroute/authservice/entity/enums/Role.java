package com.swiftroute.authservice.entity.enums;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    SYSTEM_ADMIN,
    OPS_STAFF,
    DRIVER,
    CUSTOMER,
    ADMIN,
    OWNER,
    TENANT_ADMIN,
    PARTNER;

    @Override
    public String getAuthority() {
        return "ROLE_" + this.name();
    }
}
