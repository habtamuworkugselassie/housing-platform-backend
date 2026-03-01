-- General media table for both Property and Organization (images, videos, logo).
-- Replaces property_images with a polymorphic attachment model.

CREATE TABLE IF NOT EXISTS media_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID REFERENCES properties(id) ON DELETE CASCADE,
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    image_url TEXT,
    file_data BYTEA,
    content_type VARCHAR(100),
    file_name VARCHAR(255),
    caption TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    media_kind VARCHAR(50) NOT NULL DEFAULT 'IMAGE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_media_entity CHECK (
        (property_id IS NOT NULL AND organization_id IS NULL) OR
        (property_id IS NULL AND organization_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_media_attachments_property_id ON media_attachments(property_id);
CREATE INDEX IF NOT EXISTS idx_media_attachments_organization_id ON media_attachments(organization_id);
CREATE INDEX IF NOT EXISTS idx_media_attachments_content_type ON media_attachments(content_type);
CREATE INDEX IF NOT EXISTS idx_media_attachments_media_kind ON media_attachments(media_kind);

-- Migrate existing property_images into media_attachments
INSERT INTO media_attachments (
    id, property_id, organization_id, image_url, file_data, content_type, file_name,
    caption, display_order, is_primary, media_kind, created_at, updated_at, version
)
SELECT
    id, property_id, NULL, image_url, file_data, content_type, file_name,
    caption, display_order, is_primary, 'IMAGE', created_at, updated_at, version
FROM property_images;

-- Drop old table
DROP TABLE IF EXISTS property_images;
