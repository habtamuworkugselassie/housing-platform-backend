-- Migration: Add building_id to financing_offers table
-- This allows financing offers to be linked to buildings in addition to properties

ALTER TABLE financing_offers 
ADD COLUMN IF NOT EXISTS building_id UUID REFERENCES buildings(id);

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_financing_offers_building_id ON financing_offers(building_id);
CREATE INDEX IF NOT EXISTS idx_financing_offers_building_status ON financing_offers(building_id, status);
