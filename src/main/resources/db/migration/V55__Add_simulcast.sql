-- Social simulcast: reusable RTMP destinations + egress tracking on a broadcast.

CREATE TABLE IF NOT EXISTS simulcast_target (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform VARCHAR(20) NOT NULL,
    label VARCHAR(120) NOT NULL,
    rtmp_url VARCHAR(512) NOT NULL,
    stream_key VARCHAR(512) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Egress id while a broadcast is being simulcast to social RTMP destinations.
ALTER TABLE live_broadcast ADD COLUMN IF NOT EXISTS egress_id VARCHAR(64);
