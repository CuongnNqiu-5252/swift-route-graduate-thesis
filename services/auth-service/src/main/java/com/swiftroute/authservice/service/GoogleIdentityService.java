package com.swiftroute.authservice.service;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoogleIdentityService {
    public record GoogleIdentity(
            String sub,
            String email,
            boolean emailVerified,
            String name) {
    }

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentity verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);

            if (token == null) {
                throw new AppException(
                        ErrorCode.INVALID_GOOGLE_TOKEN);
            }

            GoogleIdToken.Payload payload = token.getPayload();

            return new GoogleIdentity(
                    payload.getSubject(),
                    payload.getEmail(),
                    Boolean.TRUE.equals(payload.getEmailVerified()),
                    (String) payload.get("name"));

        } catch (GeneralSecurityException | IOException e) {
            throw new AppException(
                    ErrorCode.GOOGLE_VERIFICATION_FAILED);
        }
    }
}
