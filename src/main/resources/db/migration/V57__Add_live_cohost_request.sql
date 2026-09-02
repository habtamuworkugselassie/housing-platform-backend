-- Co-host requests: a viewer asks to publish their own camera/mic into a live broadcast;
-- the broadcaster approves, and the viewer is issued a publish token for the same room.

CREATE TABLE IF NOT EXISTS live_cohost_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcast_id UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    participant_identity VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requester_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cohost_broadcast FOREIGN KEY (broadcast_id)
        REFERENCES live_broadcast(id) ON DELETE CASCADE
);

CREATE INDEX idx_cohost_broadcast_status ON live_cohost_request(broadcast_id, status);
