package com.swiftroute.notificationservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class ChatWebSocketConfig implements WebSocketConfigurer {
    private final ChatSocket socket;
    private final String[] origins;

    ChatWebSocketConfig(ChatSocket socket, @Value("${CHAT_ALLOWED_ORIGINS:http://localhost:3000,http://127.0.0.1:3000,"
            + "http://localhost:4200,http://127.0.0.1:4200}") String origins) {
        this.socket = socket;
        this.origins = origins.split("\\s*,\\s*");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socket, "/ws/chat").setAllowedOrigins(origins);
    }
}
