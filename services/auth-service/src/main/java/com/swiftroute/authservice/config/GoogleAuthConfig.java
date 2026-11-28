package com.swiftroute.authservice.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@Configuration
public class GoogleAuthConfig {

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${google.oauth.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }
}
