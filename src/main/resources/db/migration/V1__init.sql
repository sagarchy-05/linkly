CREATE TABLE links (
                       id BIGSERIAL PRIMARY KEY,
                       short_code VARCHAR(16) NOT NULL UNIQUE,
                       long_url TEXT NOT NULL,
                       user_id BIGINT,
                       is_custom BOOLEAN NOT NULL DEFAULT FALSE,
                       expires_at TIMESTAMPTZ,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       click_count BIGINT NOT NULL DEFAULT 0,
                       is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_links_short_code ON links (short_code);
CREATE INDEX idx_links_user_id ON links (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_links_expires_at ON links (expires_at) WHERE expires_at IS NOT NULL;