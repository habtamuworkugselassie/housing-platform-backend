-- Subcategories for construction material suppliers (organization type SUPPLIER).
CREATE TABLE IF NOT EXISTS supplier_subcategories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_supplier_subcategories_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS organization_supplier_subcategories (
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    supplier_subcategory_id UUID NOT NULL REFERENCES supplier_subcategories(id) ON DELETE CASCADE,
    PRIMARY KEY (organization_id, supplier_subcategory_id)
);

CREATE INDEX IF NOT EXISTS idx_org_supplier_subcat_org ON organization_supplier_subcategories(organization_id);
CREATE INDEX IF NOT EXISTS idx_org_supplier_subcat_sub ON organization_supplier_subcategories(supplier_subcategory_id);

INSERT INTO supplier_subcategories (name, slug, sort_order, active)
VALUES
    ('Cement', 'CEMENT', 10, TRUE),
    ('Steel', 'STEEL', 20, TRUE),
    ('Finishing materials', 'FINISHING_MATERIALS', 30, TRUE),
    ('Paint', 'PAINT', 40, TRUE),
    ('Plumbing', 'PLUMBING', 50, TRUE),
    ('Electrical', 'ELECTRICAL', 60, TRUE),
    ('Wood & timber', 'WOOD_TIMBER', 70, TRUE),
    ('Roofing', 'ROOFING', 80, TRUE)
ON CONFLICT (slug) DO NOTHING;
