package com.swiftroute.authservice.dto.request;

import java.util.List;

public record UpdateMemberPermissionsRequest(
        List<String> grantedPermissions,
        List<String> revokedPermissions
) {
}
