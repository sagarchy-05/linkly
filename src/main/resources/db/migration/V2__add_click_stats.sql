CREATE TABLE click_stats (
                             id BIGSERIAL PRIMARY KEY,
                             link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,
                             bucket_hour TIMESTAMPTZ NOT NULL,
                             click_count BIGINT NOT NULL DEFAULT 0,
                             UNIQUE (link_id, bucket_hour)
);

CREATE INDEX idx_click_stats_link_hour ON click_stats(link_id, bucket_hour DESC);