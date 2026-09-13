-- Lifecycle emails for expo registrations.
--
-- Three things are added: where the lead came from (so a registration can be attributed to a
-- campaign rather than counted anonymously), a per-registrant unsubscribe token, and a send log
-- that makes every send idempotent. The log is what stops a redeploy, a clock change or a rerun
-- of the daily job from mailing the same person the same reminder twice.

ALTER TABLE exhibition_interest
    ADD COLUMN IF NOT EXISTS utm_source        VARCHAR(120),
    ADD COLUMN IF NOT EXISTS utm_medium        VARCHAR(120),
    ADD COLUMN IF NOT EXISTS utm_campaign      VARCHAR(180),
    ADD COLUMN IF NOT EXISTS utm_term          VARCHAR(180),
    ADD COLUMN IF NOT EXISTS utm_content       VARCHAR(180),
    ADD COLUMN IF NOT EXISTS referrer          VARCHAR(500),
    ADD COLUMN IF NOT EXISTS landing_path      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS unsubscribe_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS unsubscribed_at   TIMESTAMP;

-- Rows registered before this migration have no token, and an unsubscribe link is the one part
-- of a reminder that cannot be added later — the mail is already sent by then. Backfill so every
-- existing lead can opt out of the reminder series the first time one reaches them.
UPDATE exhibition_interest
SET unsubscribe_token = replace(gen_random_uuid()::text, '-', '')
                        || replace(gen_random_uuid()::text, '-', '')
WHERE unsubscribe_token IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_exhibition_interest_unsubscribe_token
    ON exhibition_interest (unsubscribe_token);

CREATE TABLE IF NOT EXISTS exhibition_interest_email (
    id          UUID         PRIMARY KEY,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    version     BIGINT,
    interest_id UUID         NOT NULL REFERENCES exhibition_interest (id) ON DELETE CASCADE,
    email_kind  VARCHAR(32)  NOT NULL,
    recipient   VARCHAR(255) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    attempts    INTEGER      NOT NULL DEFAULT 0,
    sent_at     TIMESTAMP,
    detail      VARCHAR(500),
    CONSTRAINT chk_exhibition_interest_email_status
        CHECK (status IN ('SENT', 'FAILED', 'SUPPRESSED'))
);

-- One row per (registrant, kind) is the idempotency guarantee: a second attempt updates this row
-- rather than inserting another, so a reminder can be retried after a failure but never resent
-- after a success.
CREATE UNIQUE INDEX IF NOT EXISTS ux_exhibition_interest_email_kind
    ON exhibition_interest_email (interest_id, email_kind);

CREATE INDEX IF NOT EXISTS idx_exhibition_interest_email_status
    ON exhibition_interest_email (status);
