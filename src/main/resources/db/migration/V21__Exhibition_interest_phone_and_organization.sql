-- Add phone and organization link to exhibition interest
ALTER TABLE exhibition_interest
  ADD COLUMN IF NOT EXISTS phone_number VARCHAR(50),
  ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_exhibition_interest_organization_id ON exhibition_interest(organization_id);
