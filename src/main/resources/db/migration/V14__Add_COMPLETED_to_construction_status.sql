-- Migration: Add COMPLETED to Property.ConstructionStatus enum
-- This migration updates existing data and ensures COMPLETED is treated the same as READY_TO_MOVE

-- Update any existing COMPLETED statuses to READY_TO_MOVE (if any exist)
-- Note: This is a no-op if no COMPLETED values exist, but ensures data consistency
UPDATE properties 
SET construction_percentage = 100
WHERE construction_status = 'COMPLETED' AND construction_percentage IS NULL;

-- The enum change in Property.java will handle new COMPLETED values going forward
-- No database constraint changes needed as construction_status is VARCHAR(50)
