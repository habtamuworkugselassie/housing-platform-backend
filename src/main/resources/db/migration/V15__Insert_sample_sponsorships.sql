-- Insert sample sponsorship packages
-- This migration adds sample sponsorship packages for testing and demonstration

-- Insert Basic Sponsorship Package
INSERT INTO sponsorships (
    id,
    name,
    description,
    type,
    base_price,
    features,
    status,
    notes,
    created_at,
    updated_at,
    version,
    created_by,
    updated_by
) VALUES (
    gen_random_uuid(),
    'Basic Sponsorship',
    'Basic sponsorship package for standard property listings. Perfect for small real estate companies getting started.',
    'BASIC',
    1000.00,
    'Standard listing visibility, Basic analytics dashboard, Email support, Up to 50 property listings per month',
    'ACTIVE',
    'Suitable for small real estate companies and individual agents',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'system',
    'system'
) ON CONFLICT (name) DO NOTHING;

-- Insert Premier Sponsorship Package
INSERT INTO sponsorships (
    id,
    name,
    description,
    type,
    base_price,
    features,
    status,
    notes,
    created_at,
    updated_at,
    version,
    created_by,
    updated_by
) VALUES (
    gen_random_uuid(),
    'Premier Sponsorship',
    'Premium sponsorship package with enhanced visibility and advanced features. Ideal for established real estate companies.',
    'PREMIER',
    5000.00,
    'Featured listings with priority placement, Advanced analytics dashboard, Priority customer support, Unlimited property listings, Custom branding options, Featured in search results, Monthly performance reports',
    'ACTIVE',
    'Recommended for established real estate companies with high listing volumes',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'system',
    'system'
) ON CONFLICT (name) DO NOTHING;

-- Insert Starter Sponsorship Package (Basic tier with lower price)
INSERT INTO sponsorships (
    id,
    name,
    description,
    type,
    base_price,
    features,
    status,
    notes,
    created_at,
    updated_at,
    version,
    created_by,
    updated_by
) VALUES (
    gen_random_uuid(),
    'Starter Package',
    'Entry-level sponsorship package for new real estate companies. Affordable option to get started with sponsored listings.',
    'BASIC',
    500.00,
    'Standard listing visibility, Basic analytics, Email support, Up to 25 property listings per month',
    'ACTIVE',
    'Perfect for new real estate companies testing the platform',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'system',
    'system'
) ON CONFLICT (name) DO NOTHING;

-- Insert Enterprise Sponsorship Package (Premier tier with premium features)
INSERT INTO sponsorships (
    id,
    name,
    description,
    type,
    base_price,
    features,
    status,
    notes,
    created_at,
    updated_at,
    version,
    created_by,
    updated_by
) VALUES (
    gen_random_uuid(),
    'Enterprise Package',
    'Enterprise-level sponsorship package with maximum visibility and premium features. Designed for large real estate companies.',
    'PREMIER',
    10000.00,
    'Top-tier featured listings, Dedicated account manager, 24/7 priority support, Unlimited property listings, Full custom branding, Featured in all search results, Advanced analytics with custom reports, API access, White-label options',
    'ACTIVE',
    'Best for large real estate companies requiring maximum visibility and premium support',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'system',
    'system'
) ON CONFLICT (name) DO NOTHING;

-- Insert an inactive sponsorship package for testing
INSERT INTO sponsorships (
    id,
    name,
    description,
    type,
    base_price,
    features,
    status,
    notes,
    created_at,
    updated_at,
    version,
    created_by,
    updated_by
) VALUES (
    gen_random_uuid(),
    'Legacy Package',
    'Legacy sponsorship package that is no longer available for new applications.',
    'BASIC',
    750.00,
    'Standard listing visibility, Basic analytics, Email support',
    'INACTIVE',
    'This package is no longer available for new applications',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'system',
    'system'
) ON CONFLICT (name) DO NOTHING;
