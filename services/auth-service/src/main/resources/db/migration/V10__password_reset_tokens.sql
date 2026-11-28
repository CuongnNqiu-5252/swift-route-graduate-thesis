CREATE TABLE password_reset_tokens (
    token_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_user_created
    ON password_reset_tokens(user_id, created_at DESC);
CREATE INDEX idx_password_reset_active
    ON password_reset_tokens(token_hash)
    WHERE used_at IS NULL;
