package com.swiftroute.authservice.dto.response;

import java.util.List;
import java.util.Map;

public record PermissionCatalogResponse(
        List<PermissionItemDTO> permissions,
        Map<String, List<String>> systemRoleDefaults,
        Map<String, List<String>> tenantRoleTemplates
) {
    public record PermissionItemDTO(
            String code,
            String module,
            String displayName,
            String description,
            boolean driverOnly
    ) {
    }
}
