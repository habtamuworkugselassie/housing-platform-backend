-- Admin workflow: mark exhibition registrant contact as verified before approving organization
ALTER TABLE exhibition_interest
    ADD COLUMN IF NOT EXISTS contact_verified_at TIMESTAMP;
