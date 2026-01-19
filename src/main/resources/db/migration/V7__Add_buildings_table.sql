-- Migration: Add buildings table and update properties to support buildings
-- This migration adds support for buildings that can contain multiple properties/units

-- Create buildings table
CREATE TABLE IF NOT EXISTS buildings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    address TEXT NOT NULL,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(255),
    country VARCHAR(255) NOT NULL,
    zip_code VARCHAR(50),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    total_floors INTEGER NOT NULL,
    total_units INTEGER NOT NULL,
    real_estate_company_id UUID NOT NULL,
    agent_id UUID,
    building_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    amenities TEXT,
    facilities TEXT,
    year_built INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_building_company FOREIGN KEY (real_estate_company_id) REFERENCES organizations(id),
    CONSTRAINT fk_building_agent FOREIGN KEY (agent_id) REFERENCES real_estate_agents(id)
);

-- Add building_id and unit_number to properties table
ALTER TABLE properties 
ADD COLUMN IF NOT EXISTS building_id UUID,
ADD COLUMN IF NOT EXISTS unit_number VARCHAR(50);

-- Add foreign key constraint for building_id
ALTER TABLE properties
ADD CONSTRAINT fk_property_building FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE SET NULL;

-- Create index on building_id for faster queries
CREATE INDEX IF NOT EXISTS idx_properties_building_id ON properties(building_id);

-- Create index on building real_estate_company_id
CREATE INDEX IF NOT EXISTS idx_buildings_company_id ON buildings(real_estate_company_id);

-- Create index on building agent_id
CREATE INDEX IF NOT EXISTS idx_buildings_agent_id ON buildings(agent_id);
