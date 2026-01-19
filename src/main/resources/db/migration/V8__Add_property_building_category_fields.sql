-- Migration: Add category, construction percentage, and fully furnished fields
-- This migration adds category (FOR_SALE/FOR_RENTAL), construction percentage, and is_fully_furnished flag

-- Add fields to properties table
ALTER TABLE properties 
ADD COLUMN IF NOT EXISTS category VARCHAR(50) NOT NULL DEFAULT 'FOR_SALE',
ADD COLUMN IF NOT EXISTS construction_percentage INTEGER CHECK (construction_percentage >= 0 AND construction_percentage <= 100),
ADD COLUMN IF NOT EXISTS is_fully_furnished BOOLEAN NOT NULL DEFAULT FALSE;

-- Add fields to buildings table
ALTER TABLE buildings 
ADD COLUMN IF NOT EXISTS category VARCHAR(50) NOT NULL DEFAULT 'FOR_SALE',
ADD COLUMN IF NOT EXISTS construction_percentage INTEGER CHECK (construction_percentage >= 0 AND construction_percentage <= 100),
ADD COLUMN IF NOT EXISTS is_fully_furnished BOOLEAN NOT NULL DEFAULT FALSE;

-- Update existing records: set construction percentage based on construction status
UPDATE properties 
SET construction_percentage = CASE 
    WHEN construction_status = 'READY_TO_MOVE' THEN 100
    WHEN construction_status = 'UNDER_CONSTRUCTION' THEN 50
    WHEN construction_status = 'PLANNED' THEN 0
    ELSE NULL
END
WHERE construction_percentage IS NULL;

-- Update buildings: set construction percentage based on status
UPDATE buildings 
SET construction_percentage = CASE 
    WHEN status = 'COMPLETED' THEN 100
    WHEN status = 'UNDER_CONSTRUCTION' THEN 50
    WHEN status = 'PLANNED' THEN 0
    WHEN status = 'RENOVATION' THEN 75
    ELSE NULL
END
WHERE construction_percentage IS NULL;
