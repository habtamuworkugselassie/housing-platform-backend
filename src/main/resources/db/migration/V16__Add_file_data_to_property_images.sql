-- Migration: Add file data column to property_images table for storing files directly in database
-- This allows storing images/videos as BLOB in the database instead of filesystem

-- Add file_data column (BYTEA for PostgreSQL - binary data)
ALTER TABLE property_images 
ADD COLUMN IF NOT EXISTS file_data BYTEA;

-- Add content_type column to store MIME type
ALTER TABLE property_images 
ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);

-- Add file_name column to store original filename
ALTER TABLE property_images 
ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);

-- Make image_url nullable since we can now store files in DB
ALTER TABLE property_images 
ALTER COLUMN image_url DROP NOT NULL;

-- Add index on content_type for filtering
CREATE INDEX IF NOT EXISTS idx_property_images_content_type ON property_images(content_type);
