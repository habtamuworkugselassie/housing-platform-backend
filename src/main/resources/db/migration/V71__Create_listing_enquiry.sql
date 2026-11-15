-- Listing enquiries: guest-friendly "ask about this listing / contact this company" forms.
CREATE TABLE IF NOT EXISTS listing_enquiry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(32) NOT NULL,
    property_id UUID REFERENCES properties(id) ON DELETE SET NULL,
    organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    subject_title VARCHAR(500),
    name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50),
    message TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    page_url VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_listing_enquiry_created_at ON listing_enquiry(created_at);
CREATE INDEX IF NOT EXISTS idx_listing_enquiry_status ON listing_enquiry(status);
CREATE INDEX IF NOT EXISTS idx_listing_enquiry_organization_id ON listing_enquiry(organization_id);
CREATE INDEX IF NOT EXISTS idx_listing_enquiry_property_id ON listing_enquiry(property_id);
