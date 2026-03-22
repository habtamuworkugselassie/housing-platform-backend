-- Optional sponsorship package interest when registering as exhibitor
ALTER TABLE exhibition_interest
    ADD COLUMN IF NOT EXISTS sponsorship_id UUID REFERENCES sponsorships(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_exhibition_interest_sponsorship_id ON exhibition_interest(sponsorship_id);
