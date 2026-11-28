package com.swiftroute.driverservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Size;

public record UpdateDriverDeviceRequest(
        @JsonAlias({"deviceId", "device_id", "installationId", "installation_id"})
        @Size(max = 128)
        String deviceId,

        @JsonAlias({"deviceOs", "device_os"})
        @Size(max = 30)
        String deviceOs,

        @JsonAlias({"osVersion", "os_version"})
        @Size(max = 50)
        String osVersion,

        @JsonAlias({"appVersion", "app_version"})
        @Size(max = 50)
        String appVersion,

        @JsonAlias({"deviceModel", "device_model"})
        @Size(max = 120)
        String deviceModel) {
}
