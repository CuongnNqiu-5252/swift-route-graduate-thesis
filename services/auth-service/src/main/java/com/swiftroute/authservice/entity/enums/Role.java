package com.swiftroute.authservice.entity.enums;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    SYSTEM_ADMIN,
    USER;

    @Override
    public String getAuthority() {
        return "ROLE_" + this.name();
    }
}
