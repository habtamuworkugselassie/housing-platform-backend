-- Consolidate email, website, social URLs into organization_contacts (1:1 with organizations).
-- Phones reference organization_contacts instead of organizations.

CREATE TABLE organization_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(255),
    website VARCHAR(255),
    facebook_url VARCHAR(2048),
    instagram_url VARCHAR(2048),
    linkedin_url VARCHAR(2048),
    twitter_url VARCHAR(2048),
    youtube_url VARCHAR(2048),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_organization_contacts_organization_id ON organization_contacts(organization_id);

INSERT INTO organization_contacts (
    organization_id,
    email,
    website,
    facebook_url,
    instagram_url,
    linkedin_url,
    twitter_url,
    youtube_url
)
SELECT
    id,
    email,
    website,
    facebook_url,
    instagram_url,
    linkedin_url,
    twitter_url,
    youtube_url
FROM organizations;

ALTER TABLE organization_phones ADD COLUMN contact_id UUID;

UPDATE organization_phones op
SET contact_id = (
    SELECT oc.id
    FROM organization_contacts oc
    WHERE oc.organization_id = op.organization_id
);

ALTER TABLE organization_phones ALTER COLUMN contact_id SET NOT NULL;

ALTER TABLE organization_phones DROP CONSTRAINT organization_phones_organization_id_fkey;

DROP INDEX IF EXISTS idx_organization_phones_organization_id;

ALTER TABLE organization_phones DROP COLUMN organization_id;

ALTER TABLE organization_phones
    ADD CONSTRAINT organization_phones_contact_id_fkey
    FOREIGN KEY (contact_id) REFERENCES organization_contacts(id) ON DELETE CASCADE;

CREATE INDEX idx_organization_phones_contact_id ON organization_phones(contact_id);

ALTER TABLE organizations DROP COLUMN IF EXISTS email;
ALTER TABLE organizations DROP COLUMN IF EXISTS website;
ALTER TABLE organizations DROP COLUMN IF EXISTS facebook_url;
ALTER TABLE organizations DROP COLUMN IF EXISTS instagram_url;
ALTER TABLE organizations DROP COLUMN IF EXISTS linkedin_url;
ALTER TABLE organizations DROP COLUMN IF EXISTS twitter_url;
ALTER TABLE organizations DROP COLUMN IF EXISTS youtube_url;
