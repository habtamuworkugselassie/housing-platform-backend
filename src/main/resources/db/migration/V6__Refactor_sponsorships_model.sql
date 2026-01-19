-- Refactor sponsorships to be standalone packages
-- Create sponsorship_applications table to link organizations to sponsorships

-- Drop old sponsorship columns from organizations table (if they exist)
ALTER TABLE organizations 
DROP COLUMN IF EXISTS sponsorship_type,
DROP COLUMN IF EXISTS sponsorship_start_date,
DROP COLUMN IF EXISTS sponsorship_end_date;

-- Drop old sponsorships table if it exists (will be recreated)
DROP TABLE IF EXISTS sponsorships CASCADE;

-- Create new sponsorships table (standalone packages)
CREATE TABLE IF NOT EXISTS sponsorships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    base_price NUMERIC(19, 2) NOT NULL,
    features TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Create sponsorship_applications table (links organizations to sponsorships)
CREATE TABLE IF NOT EXISTS sponsorship_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sponsorship_id UUID NOT NULL REFERENCES sponsorships(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    notes TEXT,
    rejection_reason TEXT,
    amount NUMERIC(19, 2),
    payment_reference VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT unique_org_sponsorship_active UNIQUE (organization_id, sponsorship_id, status) 
    DEFERRABLE INITIALLY DEFERRED
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_sponsorships_type ON sponsorships(type);
CREATE INDEX IF NOT EXISTS idx_sponsorships_status ON sponsorships(status);
CREATE INDEX IF NOT EXISTS idx_sponsorship_applications_org ON sponsorship_applications(organization_id);
CREATE INDEX IF NOT EXISTS idx_sponsorship_applications_sponsorship ON sponsorship_applications(sponsorship_id);
CREATE INDEX IF NOT EXISTS idx_sponsorship_applications_status ON sponsorship_applications(status);
CREATE INDEX IF NOT EXISTS idx_sponsorship_applications_dates ON sponsorship_applications(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_sponsorship_applications_active ON sponsorship_applications(organization_id, status, start_date, end_date)
WHERE status = 'APPROVED';
