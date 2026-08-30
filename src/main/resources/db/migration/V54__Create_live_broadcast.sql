CREATE TABLE IF NOT EXISTS live_broadcast (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room VARCHAR(100) NOT NULL,
    title VARCHAR(160) NOT NULL,
    broadcaster_name VARCHAR(120) NOT NULL,
    broadcaster_email VARCHAR(255),
    broadcaster_user_id UUID,
    broadcaster_role VARCHAR(20) NOT NULL DEFAULT 'VISITOR',
    company_name VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    hls_url VARCHAR(512),
    ingress_id VARCHAR(64),
    requester_ip VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_live_broadcast_room UNIQUE (room)
);

CREATE INDEX idx_live_broadcast_status ON live_broadcast(status);
CREATE INDEX idx_live_broadcast_created_at ON live_broadcast(created_at);
CREATE INDEX idx_live_broadcast_requester_ip ON live_broadcast(requester_ip);
