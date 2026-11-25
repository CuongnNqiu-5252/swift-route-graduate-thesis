package com.swiftroute.notificationservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
class RecipientDeliveryOutcomesController {
    private final RecipientDeliveryOutcomes outcomes;
    @Value("${TENANT_INTERNAL_KEY:}")
    private String serviceKey;

    @PostMapping("/internal/recipient-delivery-outcomes")
    public Map<UUID, String> outcomes(@RequestHeader("X-Service-Key") String supplied, @RequestBody List<UUID> eventIds) {
        if (serviceKey.isBlank() || !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (eventIds.isEmpty() || eventIds.size() > 100 || eventIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return outcomes.read(eventIds);
    }
}
