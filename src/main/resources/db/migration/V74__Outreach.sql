-- B2B outreach sequencer: curated prospect contacts, per-sector templates, sent-message log.
CREATE TABLE IF NOT EXISTS outreach_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(300) NOT NULL,
    contact_name VARCHAR(200),
    sector VARCHAR(100) NOT NULL,
    template_key VARCHAR(40),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(100),
    website VARCHAR(500),
    city VARCHAR(200),
    locale VARCHAR(8) NOT NULL DEFAULT 'en',
    confidence VARCHAR(100),
    source_url VARCHAR(1000),
    notes TEXT,
    hook VARCHAR(500),
    owner VARCHAR(100),
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    step INTEGER NOT NULL DEFAULT 0,
    next_action_at TIMESTAMP,
    last_sent_at TIMESTAMP,
    replied_at TIMESTAMP,
    thread_id VARCHAR(100),
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_outreach_contact_status ON outreach_contact(status);
CREATE INDEX IF NOT EXISTS idx_outreach_contact_next_action ON outreach_contact(next_action_at);

CREATE TABLE IF NOT EXISTS outreach_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(40) NOT NULL,
    locale VARCHAR(8) NOT NULL,
    subject VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_outreach_template UNIQUE (template_key, locale)
);

CREATE TABLE IF NOT EXISTS outreach_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL REFERENCES outreach_contact(id) ON DELETE CASCADE,
    step INTEGER NOT NULL,
    subject VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    provider_message_id VARCHAR(100),
    thread_id VARCHAR(100),
    sent_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_outreach_message_contact ON outreach_message(contact_id);
