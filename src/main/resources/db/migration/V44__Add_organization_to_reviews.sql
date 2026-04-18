ALTER TABLE reviews
    ADD COLUMN organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    ALTER COLUMN property_id DROP NOT NULL;
