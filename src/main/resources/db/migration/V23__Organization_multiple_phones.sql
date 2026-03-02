-- Organization multiple phone numbers with country code
CREATE TABLE organization_phones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    country_code VARCHAR(10) NOT NULL DEFAULT '+251',
    number VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_organization_phones_organization_id ON organization_phones(organization_id);

-- Migrate existing phone_number: one row per org (country_code +251, number = local part or full)
INSERT INTO organization_phones (organization_id, country_code, number, display_order)
SELECT id,
       '+251',
       COALESCE(TRIM(phone_number), ''),
       0
FROM organizations;

ALTER TABLE organizations DROP COLUMN IF EXISTS phone_number;
