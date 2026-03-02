-- One-off repair: Flyway shows V19–V22 as executed but tables were never created.
-- Run this ONCE on the database where the tables are missing (e.g. prod).
-- Safe to run: uses IF NOT EXISTS / IF NOT EXISTS so it won’t fail if objects already exist.
--
-- Connect: psql $DATABASE_URL -f scripts/repair-missing-tables-from-flyway.sql

BEGIN;

-- ========== V19: media_attachments (structure only; no migration from property_images, no drop) ==========
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

-- ========== V20: exhibition_interest ==========
CREATE TABLE IF NOT EXISTS exhibition_interest (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    interest_type VARCHAR(50) NOT NULL,
    company VARCHAR(500),
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_exhibition_interest_email ON exhibition_interest(email);
CREATE INDEX IF NOT EXISTS idx_exhibition_interest_created_at ON exhibition_interest(created_at);
CREATE INDEX IF NOT EXISTS idx_exhibition_interest_interest_type ON exhibition_interest(interest_type);

-- ========== V21: exhibition_interest extra columns ==========
ALTER TABLE exhibition_interest
  ADD COLUMN IF NOT EXISTS phone_number VARCHAR(50),
  ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_exhibition_interest_organization_id ON exhibition_interest(organization_id);

COMMIT;

-- Optional: if you still have property_images and want to migrate into media_attachments, run manually:
-- INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_data, content_type, file_name, caption, display_order, is_primary, media_kind, created_at, updated_at, version)
-- SELECT id, property_id, NULL, image_url, file_data, content_type, file_name, caption, display_order, is_primary, 'IMAGE', created_at, updated_at, version FROM property_images;
-- DROP TABLE IF EXISTS property_images;
