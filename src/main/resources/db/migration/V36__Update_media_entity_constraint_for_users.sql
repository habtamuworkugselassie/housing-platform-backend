-- General media table for Property, Organization, and User
-- Replaces old constraint with one that allows user_id

-- Ensure user_id column exists (in case it wasn't added properly)
ALTER TABLE media_attachments ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Drop old constraint created in V19
ALTER TABLE media_attachments DROP CONSTRAINT IF EXISTS chk_media_entity;

-- Add updated constraint allowing exactly one of the three reference IDs to be NOT NULL
ALTER TABLE media_attachments ADD CONSTRAINT chk_media_entity CHECK (
    (property_id IS NOT NULL AND organization_id IS NULL AND user_id IS NULL) OR
    (property_id IS NULL AND organization_id IS NOT NULL AND user_id IS NULL) OR
    (property_id IS NULL AND organization_id IS NULL AND user_id IS NOT NULL)
);

-- Ensure index on user_id
CREATE INDEX IF NOT EXISTS idx_media_attachments_user_id ON media_attachments(user_id);
