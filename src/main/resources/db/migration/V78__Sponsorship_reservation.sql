-- Sponsor package reservations: the sales pipeline from "reserve" to "paid".
CREATE TABLE IF NOT EXISTS sponsorship_reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(20) NOT NULL,
    sponsorship_id UUID REFERENCES sponsorships(id) ON DELETE SET NULL,
    package_name VARCHAR(200) NOT NULL,
    package_type VARCHAR(40),
    amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'ETB',
    company VARCHAR(200) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    tin VARCHAR(40),
    booth_preference VARCHAR(200),
    message TEXT,
    locale VARCHAR(8) NOT NULL DEFAULT 'en',
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    amount_paid NUMERIC(19,2) NOT NULL DEFAULT 0,
    admin_notes TEXT,
    assigned_to VARCHAR(120),
    source VARCHAR(40),
    agreement_sent_at TIMESTAMP,
    signed_at TIMESTAMP,
    paid_at TIMESTAMP,
    last_reminder_at TIMESTAMP,
    reminder_count INTEGER NOT NULL DEFAULT 0,
    status_changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sponsorship_reservation_reference ON sponsorship_reservation (reference);
CREATE INDEX IF NOT EXISTS ix_sponsorship_reservation_status ON sponsorship_reservation (status);
CREATE INDEX IF NOT EXISTS ix_sponsorship_reservation_created ON sponsorship_reservation (created_at DESC);
