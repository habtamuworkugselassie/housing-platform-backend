-- Online (Chapa) payments against a sponsorship reservation, and the visitor reminder marker.
CREATE TABLE IF NOT EXISTS reservation_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES sponsorship_reservation(id) ON DELETE CASCADE,
    tx_ref VARCHAR(64) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'ETB',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checkout_url VARCHAR(1000),
    method VARCHAR(40),
    provider_reference VARCHAR(120),
    failure_reason VARCHAR(500),
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_reservation_payment_txref ON reservation_payment (tx_ref);
CREATE INDEX IF NOT EXISTS ix_reservation_payment_reservation ON reservation_payment (reservation_id);

ALTER TABLE visitor_registration ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMP;
