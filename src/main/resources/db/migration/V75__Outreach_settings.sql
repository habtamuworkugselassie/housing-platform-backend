-- Scheduler switch and caps, editable from the admin page (no restart). One row.
CREATE TABLE IF NOT EXISTS outreach_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_cap INTEGER NOT NULL DEFAULT 30,
    hourly_cap INTEGER NOT NULL DEFAULT 8,
    send_window_start INTEGER NOT NULL DEFAULT 9,
    send_window_end INTEGER NOT NULL DEFAULT 17,
    follow_up_after_days INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
