-- Add latitude and longitude to organizations for HQ/branch location
ALTER TABLE organizations
  ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
