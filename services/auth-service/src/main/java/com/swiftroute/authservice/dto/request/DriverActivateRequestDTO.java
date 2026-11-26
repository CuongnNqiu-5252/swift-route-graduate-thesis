package com.swiftroute.authservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverActivateRequestDTO(
        @JsonAlias({"activationKey", "activation_key"})
        @NotBlank(message = "Mã kích hoạt không được để trống")
        @Size(max = 32, message = "Mã kích hoạt không hợp lệ")
        String activationKey,

        @JsonAlias({"deviceId", "device_id", "installationId", "installation_id"})
        @NotBlank(message = "Mã định danh thiết bị là bắt buộc")
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
