package com.swiftroute.authservice.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRolePermissionsRequest(
        @NotNull(message = "Permissions list is required")
        List<String> permissions
) {
}
