CREATE TABLE click_stats (
                             id BIGSERIAL PRIMARY KEY,
                             link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,
                             bucket_hour TIMESTAMPTZ NOT NULL,
                             click_count BIGINT NOT NULL DEFAULT 0,
                             UNIQUE (link_id, bucket_hour)
);

CREATE INDEX idx_click_stats_link_hour ON click_stats(link_id, bucket_hour DESC);

CREATE TABLE click_events_raw (
                                  id BIGSERIAL PRIMARY KEY,
                                  link_id BIGINT NOT NULL,
                                  ip_hash VARCHAR(64),
                                  referrer TEXT,
                                  user_agent TEXT,
                                  country_code VARCHAR(2),
                                  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_click_events_link ON click_events_raw(link_id, occurred_at DESC);