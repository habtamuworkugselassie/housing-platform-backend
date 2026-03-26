-- Two-step admin verification before approving sponsorship applications
ALTER TABLE sponsorship_applications
    ADD COLUMN IF NOT EXISTS organization_verified_at TIMESTAMP;
ALTER TABLE sponsorship_applications
    ADD COLUMN IF NOT EXISTS user_verified_at TIMESTAMP;
