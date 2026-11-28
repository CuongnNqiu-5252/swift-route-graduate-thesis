package com.swiftroute.orderservice.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrackingTokenService {

    private final byte[] secret;

    public TrackingTokenService(
            @Value("${tracking.secret}") String secret) {
        this.secret = Base64.getDecoder().decode(secret);

        if (this.secret.length < 32) {
            throw new IllegalArgumentException(
                    "Tracking secret must be at least 32 bytes");
        }
    }

    public String generateToken(UUID trackingId) {
        String payload = trackingId.toString();

        return payload + "." + sign(payload);
    }

    public UUID validateToken(String token) {
        String[] parts = token.split("\\.", -1);

        if (parts.length != 2) {
            throw invalidToken();
        }

        String payload = parts[0];
        String signature = parts[1];

        String expectedSignature = sign(payload);

        boolean valid = MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            throw invalidToken();
        }

        try {
            return UUID.fromString(payload);
        } catch (IllegalArgumentException e) {
            throw invalidToken();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec key = new SecretKeySpec(
                    secret,
                    "HmacSHA256");

            mac.init(key);

            byte[] signature = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(signature);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Cannot sign tracking token", e);
        }
    }

    private RuntimeException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Invalid tracking link");
    }
}
