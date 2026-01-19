-- Add real estate agents table and update properties table

-- Real Estate Agents table
CREATE TABLE IF NOT EXISTS real_estate_agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    license_number TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT unique_org_user UNIQUE (organization_id, user_id)
);

-- Add agent_id column to properties table
ALTER TABLE properties ADD COLUMN IF NOT EXISTS agent_id UUID REFERENCES real_estate_agents(id) ON DELETE SET NULL;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_real_estate_agents_user_id ON real_estate_agents(user_id);
CREATE INDEX IF NOT EXISTS idx_real_estate_agents_organization_id ON real_estate_agents(organization_id);
CREATE INDEX IF NOT EXISTS idx_properties_agent_id ON properties(agent_id);
CREATE INDEX IF NOT EXISTS idx_properties_status ON properties(status);
