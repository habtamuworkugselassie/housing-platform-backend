-- Admin review status and comments for organization verification documents (not exposed to public API).
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS business_registration_review_status VARCHAR(32);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS business_registration_review_comment TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS license_review_status VARCHAR(32);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS license_review_comment TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS vat_registration_review_status VARCHAR(32);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS vat_registration_review_comment TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS tin_registration_review_status VARCHAR(32);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS tin_registration_review_comment TEXT;
