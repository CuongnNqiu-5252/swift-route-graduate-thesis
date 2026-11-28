CREATE TABLE chat_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    client_message_id UUID NOT NULL,
    body VARCHAR(4000),
    has_image BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (sender_id <> recipient_id),
    CHECK ((body IS NOT NULL AND length(btrim(body)) > 0) OR has_image),
    UNIQUE (tenant_id, sender_id, client_message_id)
);

CREATE INDEX chat_messages_sent ON chat_messages (tenant_id, sender_id, recipient_id, id DESC);
CREATE INDEX chat_messages_received ON chat_messages (tenant_id, recipient_id, sender_id, id DESC);

CREATE TABLE chat_read_states (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    peer_id UUID NOT NULL,
    last_read_message_id BIGINT NOT NULL REFERENCES chat_messages (id),
    PRIMARY KEY (tenant_id, user_id, peer_id)
);

CREATE TABLE chat_images (
    message_id BIGINT PRIMARY KEY REFERENCES chat_messages (id) ON DELETE CASCADE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    contents BYTEA NOT NULL CHECK (octet_length(contents) BETWEEN 1 AND 5242880)
);
