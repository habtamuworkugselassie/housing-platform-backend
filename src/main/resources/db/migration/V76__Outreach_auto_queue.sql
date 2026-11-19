-- When on, the scheduler also emails NEW contacts by itself, oldest first, under the same caps.
ALTER TABLE outreach_settings ADD COLUMN IF NOT EXISTS auto_queue_new BOOLEAN NOT NULL DEFAULT TRUE;
