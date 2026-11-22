-- Payment follow-up: a signed reservation is due within N business days; overdue ones get
-- "payment due" emails and show as overdue in the pipeline.
ALTER TABLE sponsorship_reservation ADD COLUMN IF NOT EXISTS payment_due_at TIMESTAMP;
ALTER TABLE sponsorship_reservation ADD COLUMN IF NOT EXISTS payment_reminder_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sponsorship_reservation ADD COLUMN IF NOT EXISTS last_payment_reminder_at TIMESTAMP;
