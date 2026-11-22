-- A reservation also records the lead (exhibition interest) it belongs to.
ALTER TABLE sponsorship_reservation ADD COLUMN IF NOT EXISTS exhibition_interest_id UUID REFERENCES exhibition_interest(id);
CREATE INDEX IF NOT EXISTS idx_sponsorship_reservation_interest ON sponsorship_reservation(exhibition_interest_id);
ALTER TABLE exhibition_interest ADD COLUMN IF NOT EXISTS reserve_invited_at TIMESTAMP;
