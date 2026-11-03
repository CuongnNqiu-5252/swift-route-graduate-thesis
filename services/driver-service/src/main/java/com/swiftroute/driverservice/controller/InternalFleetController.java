package com.swiftroute.driverservice.controller;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.driverservice.repository.DriverRepository;
import com.swiftroute.driverservice.entity.enums.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/fleet")
public class InternalFleetController {
    private final DriverRepository drivers;
    @Value("${tenant.internal-key:}") private String key;
    @GetMapping("/{tenantId}")
    public List<Map<String,Object>> eligible(@PathVariable UUID tenantId,@RequestHeader("X-Service-Key") String supplied) {
        if (key.isBlank() || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return drivers.findAllByTenantId(tenantId).stream()
            .filter(x -> x.getStatus()==DriverStatus.ACTIVE && x.getDriverType()==DriverType.DEDICATED && x.getUserId()!=null && x.getCapacity()!=null && x.getCapacity()>0)
            .map(x -> Map.<String,Object>of("driver_id",x.getId(),"user_id",x.getUserId())).toList();
    }
}
