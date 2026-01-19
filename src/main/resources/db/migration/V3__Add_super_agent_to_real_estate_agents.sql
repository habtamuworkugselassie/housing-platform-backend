-- Add is_super_agent column to real_estate_agents table

ALTER TABLE real_estate_agents 
ADD COLUMN IF NOT EXISTS is_super_agent BOOLEAN NOT NULL DEFAULT false;

-- Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_real_estate_agents_org_super ON real_estate_agents(organization_id, is_super_agent) WHERE is_super_agent = true;
