-- Add sponsorship fields to organizations table

ALTER TABLE organizations 
ADD COLUMN IF NOT EXISTS sponsorship_type VARCHAR(50) NOT NULL DEFAULT 'NONE',
ADD COLUMN IF NOT EXISTS sponsorship_start_date TIMESTAMP,
ADD COLUMN IF NOT EXISTS sponsorship_end_date TIMESTAMP;

-- Create index for better query performance on sponsored organizations
CREATE INDEX IF NOT EXISTS idx_organizations_sponsorship_type ON organizations(sponsorship_type) 
WHERE sponsorship_type != 'NONE';

-- Create index for active sponsorships
CREATE INDEX IF NOT EXISTS idx_organizations_active_sponsorship ON organizations(sponsorship_type, sponsorship_start_date, sponsorship_end_date)
WHERE sponsorship_type != 'NONE' 
  AND sponsorship_start_date IS NOT NULL 
  AND sponsorship_end_date IS NOT NULL;
