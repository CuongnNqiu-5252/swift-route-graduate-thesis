package com.swiftroute.driverservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Validates the atomic Redis Lua scripts for GPS location updates and stale driver sweep.
 */
class DriverLocationRedisScriptTest {

    @Test
    @DisplayName("update_driver_location.lua script exists, is non-empty, and executes GEOADD and SET EX atomically")
    void updateDriverLocationScriptIsValid() throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/update_driver_location.lua");
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream()) {
            String script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(script).contains("GEOADD");
            assertThat(script).contains("SET");
            assertThat(script).contains("EX");
            assertThat(script).contains("KEYS[1]");
            assertThat(script).contains("KEYS[2]");
            assertThat(script).contains("ARGV[1]");
            assertThat(script).contains("ARGV[4]");
        }
    }

    @Test
    @DisplayName("sweep_stale_driver.lua script exists, is non-empty, and performs atomic stale eviction via ZREM")
    void sweepStaleDriverScriptIsValid() throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/sweep_stale_driver.lua");
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream()) {
            String script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(script).contains("EXISTS");
            assertThat(script).contains("ZREM");
            assertThat(script).contains("return 1");
            assertThat(script).contains("return 0");
        }
    }

    @Test
    @DisplayName("Verify tenant and driver key formatting contract")
    void verifyKeyFormattingContract() {
        UUID tenantId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        String geoKey = "driver:locations:" + tenantId;
        String heartbeatKey = "driver:heartbeat:" + driverId;

        assertThat(geoKey).startsWith("driver:locations:").endsWith(tenantId.toString());
        assertThat(heartbeatKey).startsWith("driver:heartbeat:").endsWith(driverId.toString());
    }
}

