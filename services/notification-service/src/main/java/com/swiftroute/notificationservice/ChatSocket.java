package com.swiftroute.notificationservice;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.swiftroute.notificationservice.ChatAccess.Account;

@Component
class ChatSocket extends TextWebSocketHandler {
    private record Ticket(Account account, Instant expiresAt) {}

    private static final class Connection {
        final WebSocketSession session;
        final Instant openedAt = Instant.now();
        volatile Account account;
        long rateWindow = System.nanoTime();
        int frames;

        Connection(WebSocketSession session) {
            this.session = new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024);
        }
    }

    private final ConcurrentMap<UUID, Ticket> tickets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final ChatAccess access;
    private final ChatMessages messages;

    ChatSocket(ChatAccess access, ChatMessages messages) {
        this.access = access;
        this.messages = messages;
    }

    synchronized UUID ticket(Account account) {
        if (tickets.size() >= 2000) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        }
        UUID id = UUID.randomUUID();
        tickets.put(id, new Ticket(account, Instant.now().plusSeconds(30)));
        return id;
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (connections.size() >= 500) {
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        session.setTextMessageSizeLimit(32 * 1024);
        connections.put(session.getId(), new Connection(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage frame) throws IOException {
        var connection = connections.get(session.getId());
        if (connection == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (System.nanoTime() - connection.rateWindow >= 1_000_000_000L) {
            connection.rateWindow = System.nanoTime();
            connection.frames = 0;
        }
        if (++connection.frames > 10) {
            close(connection, CloseStatus.POLICY_VIOLATION);
            return;
        }
        String clientId = "";
        try {
            JsonNode data = json.readTree(frame.getPayload());
            String type = data.path("type").asText();
            if (connection.account == null) {
                authenticate(connection, type, data);
                return;
            }
            if (!connection.account.expiresAt().isAfter(Instant.now())) {
                close(connection, CloseStatus.POLICY_VIOLATION);
                return;
            }
            clientId = data.path("client_message_id").asText();
            if (!"message.send".equals(type) || !data.path("body").isTextual()) {
                throw new IllegalArgumentException("Invalid message");
            }
            UUID peer = UUID.fromString(data.path("peer_id").asText());
            access.requirePeer(connection.account, peer);
            var saved = messages.save(connection.account, peer, UUID.fromString(clientId),
                    data.path("body").asText(), null);
            publish(connection.account.tenant(), saved);
        } catch (ResponseStatusException ex) {
            if (connection.account == null || ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                close(connection, CloseStatus.POLICY_VIOLATION);
                return;
            }
            send(connection, Map.of("type", "message.error", "client_message_id", clientId,
                    "code", ex.getStatusCode().value()));
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            if (connection.account == null) {
                close(connection, CloseStatus.POLICY_VIOLATION);
                return;
            }
            send(connection, Map.of("type", "message.error", "client_message_id", clientId, "code", 400));
        }
    }

    private void authenticate(Connection connection, String type, JsonNode data) {
        if (!"authenticate".equals(type) || connection.openedAt.plusSeconds(8).isBefore(Instant.now())) {
            close(connection, CloseStatus.POLICY_VIOLATION);
            return;
        }
        var ticket = tickets.remove(UUID.fromString(data.path("ticket").asText()));
        if (ticket == null || !ticket.expiresAt().isAfter(Instant.now())) {
            close(connection, CloseStatus.POLICY_VIOLATION);
            return;
        }
        access.contacts(ticket.account());
        connection.account = ticket.account();
        send(connection, Map.of("type", "ready", "user_id", ticket.account().user()));
    }

    void publish(UUID tenant, ChatMessages.Message message) {
        var event = Map.of("type", "message.saved", "message", message);
        sendTo(tenant, message.senderId(), event);
        sendTo(tenant, message.recipientId(), event);
    }

    void read(UUID tenant, UUID user, UUID peer) {
        sendTo(tenant, user, Map.of("type", "inbox.updated", "peer_id", peer));
    }

    private void sendTo(UUID tenant, UUID user, Object event) {
        for (var connection : connections.values()) {
            var account = connection.account;
            if (account != null && tenant.equals(account.tenant()) && user.equals(account.user())
                    && account.expiresAt().isAfter(Instant.now())) {
                var live = NotificationAuthorization.getLiveSnapshot(tenant, user);
                if (live != null && !"ACTIVE".equals(live.status())) {
                    close(connection, CloseStatus.POLICY_VIOLATION);
                    continue;
                }
                send(connection, event);
            }
        }
    }

    private void send(Connection connection, Object event) {
        try {
            connection.session.sendMessage(new TextMessage(json.writeValueAsString(event)));
        } catch (IOException | RuntimeException ex) {
            close(connection, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(Connection connection, CloseStatus status) {
        connections.remove(connection.session.getId(), connection);
        try {
            connection.session.close(status);
        } catch (IOException ignored) {
            // A failed transport can already be closed by the client.
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
    }

    @Scheduled(fixedDelay = 5000)
    void expireConnections() {
        Instant now = Instant.now();
        tickets.values().removeIf(ticket -> !ticket.expiresAt().isAfter(now));
        for (var connection : connections.values()) {
            var account = connection.account;
            var live = account == null ? null : NotificationAuthorization.getLiveSnapshot(account.tenant(), account.user());
            if (account == null ? connection.openedAt.plusSeconds(8).isBefore(now)
                    : !account.expiresAt().isAfter(now) || (live != null && !"ACTIVE".equals(live.status()))) {
                close(connection, CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    @Scheduled(fixedDelay = 25000)
    void keepAlive() {
        connections.values().stream().filter(connection -> connection.account != null)
                .forEach(connection -> send(connection, Map.of("type", "heartbeat")));
    }
}
