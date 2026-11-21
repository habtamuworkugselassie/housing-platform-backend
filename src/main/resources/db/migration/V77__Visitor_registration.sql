-- Visitor pre-registration for the expo: one row per badge, code printed in the QR.
CREATE TABLE IF NOT EXISTS visitor_registration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(16) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    organization VARCHAR(200),
    visitor_type VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    interests VARCHAR(1000),
    locale VARCHAR(8) NOT NULL DEFAULT 'en',
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    checked_in_at TIMESTAMP,
    checked_in_by VARCHAR(255),
    source VARCHAR(40),
    badge_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_visitor_registration_code ON visitor_registration (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_visitor_registration_email ON visitor_registration (LOWER(email));
CREATE INDEX IF NOT EXISTS ix_visitor_registration_created ON visitor_registration (created_at DESC);
CREATE INDEX IF NOT EXISTS ix_visitor_registration_status ON visitor_registration (status);
