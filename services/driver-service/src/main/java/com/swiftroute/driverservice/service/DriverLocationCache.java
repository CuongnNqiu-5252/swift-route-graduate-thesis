package com.swiftroute.driverservice.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.swiftroute.driverservice.dto.request.UpdateLocationRequest;
import com.swiftroute.driverservice.entity.Driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverLocationCache {
    private static final DefaultRedisScript<Long> UPDATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'sequence')
            if current and tonumber(ARGV[1]) <= tonumber(current) then return 0 end
            redis.call('HSET', KEYS[1],
              'sequence', ARGV[1], 'lat', ARGV[2], 'lng', ARGV[3],
              'accuracy_m', ARGV[4], 'speed_mps', ARGV[5], 'bearing', ARGV[6],
              'recorded_at', ARGV[7])
            redis.call('EXPIRE', KEYS[1], ARGV[8])
            redis.call('GEOADD', KEYS[2], ARGV[3], ARGV[2], ARGV[9])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    @Value("${tracking.location-ttl-seconds:300}")
    private long ttlSeconds;

    public boolean accept(Driver driver, UpdateLocationRequest request) {
        String stateKey = "driver:location:" + driver.getUserId();
        String geoKey = "driver:locations:" + driver.getTenantId();
        try {
            Long result = redis.execute(
                    UPDATE_SCRIPT,
                    List.of(stateKey, geoKey),
                    Long.toString(request.effectiveSequence()),
                    Double.toString(request.lat()),
                    Double.toString(request.lng()),
                    value(request.accuracyM()),
                    value(request.speedMps()),
                    value(request.bearing()),
                    request.effectiveRecordedAt().toString(),
                    Long.toString(ttlSeconds),
                    driver.getUserId().toString());
            return result == null || result == 1L;
        } catch (RuntimeException error) {
            log.warn("Redis location cache unavailable; accepting durable GPS update for driver {}: {}",
                    driver.getUserId(), error.getMessage());
            return true;
        }
    }

    public Driver enrichWithCachedLocation(Driver driver) {
        if (driver == null || driver.getUserId() == null) {
            return driver;
        }
        String stateKey = "driver:location:" + driver.getUserId();
        try {
            List<Object> coords = redis.opsForHash().multiGet(stateKey, List.of("lat", "lng"));
            if (coords != null && coords.size() == 2 && coords.get(0) != null && coords.get(1) != null) {
                driver.setCurrentLat(Double.parseDouble(coords.get(0).toString()));
                driver.setCurrentLng(Double.parseDouble(coords.get(1).toString()));
            }
        } catch (RuntimeException error) {
            log.debug("Unable to read cached GPS location for driver {}: {}", driver.getUserId(), error.getMessage());
        }
        return driver;
    }

    private String value(Double value) {
        return value == null ? "" : Double.toString(value);
    }
}
