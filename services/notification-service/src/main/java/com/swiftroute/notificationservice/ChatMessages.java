package com.swiftroute.notificationservice;

import static com.swiftroute.notificationservice.ChatAccess.Account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatMessages {
    record Message(long id, UUID senderId, UUID recipientId, UUID clientMessageId,
            String body, boolean hasImage, String createdAt) {}
    record InboxItem(UUID peerId, long lastId, String body, boolean hasImage, Instant createdAt, int unread) {}
    record Image(String contentType, byte[] contents) {}

    private final JdbcTemplate db;

    ChatMessages(JdbcTemplate db) {
        this.db = db;
    }

    List<InboxItem> inbox(Account account) {
        return db.query("""
                WITH mine AS (
                    SELECT *, CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END AS peer
                    FROM chat_messages WHERE tenant_id = ? AND (sender_id = ? OR recipient_id = ?)
                ), newest AS (
                    SELECT DISTINCT ON (peer) * FROM mine ORDER BY peer, id DESC
                ), unread AS (
                    SELECT mine.peer, count(*) AS total
                    FROM mine LEFT JOIN chat_read_states r
                      ON r.tenant_id = mine.tenant_id AND r.user_id = mine.recipient_id AND r.peer_id = mine.peer
                    WHERE mine.recipient_id = ? AND mine.id > coalesce(r.last_read_message_id, 0)
                    GROUP BY mine.peer
                )
                SELECT newest.*, coalesce(unread.total, 0) AS unread_count
                FROM newest LEFT JOIN unread USING (peer) ORDER BY id DESC
                """, (rs, row) -> new InboxItem(rs.getObject("peer", UUID.class), rs.getLong("id"),
                        rs.getString("body"), rs.getBoolean("has_image"), rs.getTimestamp("created_at").toInstant(),
                        rs.getInt("unread_count")),
                account.user(), account.tenant(), account.user(), account.user(), account.user());
    }

    List<Message> history(Account account, UUID peer, long before) {
        return db.query("""
                SELECT * FROM chat_messages
                WHERE tenant_id = ? AND id < ?
                  AND ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))
                ORDER BY id DESC LIMIT 50
                """, (rs, row) -> message(rs), account.tenant(), before, account.user(), peer, peer, account.user());
    }

    @Transactional
    public Message save(Account account, UUID peer, UUID clientId, String input, Image image) {
        String body = input == null || input.isBlank() ? null : input.strip();
        if (clientId == null || (body == null && image == null) || (body != null && body.length() > 4000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tin nhắn không hợp lệ");
        }
        UUID first = account.user().compareTo(peer) < 0 ? account.user() : peer;
        UUID second = first.equals(peer) ? account.user() : peer;
        // Serialize each pair so a read cursor cannot skip a concurrent, later-committing message.
        db.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null,
                account.tenant() + ":" + first + ":" + second);
        var inserted = db.query("""
                INSERT INTO chat_messages (tenant_id, sender_id, recipient_id, client_message_id, body, has_image)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, sender_id, client_message_id) DO NOTHING RETURNING *
                """, (rs, row) -> message(rs), account.tenant(), account.user(), peer, clientId, body, image != null);
        if (inserted.isEmpty()) {
            var existing = db.queryForObject("""
                    SELECT * FROM chat_messages WHERE tenant_id = ? AND sender_id = ? AND client_message_id = ?
                    """, (rs, row) -> message(rs), account.tenant(), account.user(), clientId);
            if (existing == null || !peer.equals(existing.recipientId()) || existing.hasImage() != (image != null)
                    || !java.util.Objects.equals(existing.body(), body)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            return existing;
        }
        var saved = inserted.getFirst();
        if (image != null) {
            db.update("INSERT INTO chat_images (message_id, content_type, contents) VALUES (?, ?, ?)",
                    saved.id(), image.contentType(), image.contents());
        }
        return saved;
    }

    void markRead(Account account, UUID peer, long id) {
        db.update("""
                INSERT INTO chat_read_states (tenant_id, user_id, peer_id, last_read_message_id)
                SELECT tenant_id, recipient_id, sender_id, id FROM chat_messages
                WHERE id = ? AND tenant_id = ? AND recipient_id = ? AND sender_id = ?
                ON CONFLICT (tenant_id, user_id, peer_id) DO UPDATE SET
                last_read_message_id = greatest(chat_read_states.last_read_message_id, excluded.last_read_message_id)
                """, id, account.tenant(), account.user(), peer);
    }

    UUID imagePeer(Account account, long id) {
        return db.query("""
                SELECT CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END
                FROM chat_messages WHERE id = ? AND tenant_id = ? AND has_image
                  AND (sender_id = ? OR recipient_id = ?)
                """, (rs, row) -> rs.getObject(1, UUID.class),
                account.user(), id, account.tenant(), account.user(), account.user())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    Image image(long id) {
        return db.query("SELECT content_type, contents FROM chat_images WHERE message_id = ?",
                (rs, row) -> new Image(rs.getString(1), rs.getBytes(2)), id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static Message message(ResultSet rs) throws SQLException {
        return new Message(rs.getLong("id"), rs.getObject("sender_id", UUID.class),
                rs.getObject("recipient_id", UUID.class), rs.getObject("client_message_id", UUID.class),
                rs.getString("body"), rs.getBoolean("has_image"), rs.getTimestamp("created_at").toInstant().toString());
    }
}
