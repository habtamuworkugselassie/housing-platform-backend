-- Insert sample real estate companies and properties from the realestates folder.
-- Logo and sample images are served from classpath:realestates/ (ayat, temer, metropolitan, ovid).
-- Image paths: /realestates/<folder>/<filename>

DO $$
DECLARE
    u_id UUID;
    o_id UUID;
    a_id UUID;
    p_id UUID;
    -- Ayat
    ayat_org_id UUID;
    ayat_agent_id UUID;
    ayat_prop_id UUID;
    -- Temer
    temer_org_id UUID;
    temer_agent_id UUID;
    temer_prop_id UUID;
    -- Metropolitan
    metro_org_id UUID;
    metro_agent_id UUID;
    metro_prop_id UUID;
    -- Ovid
    ovid_org_id UUID;
    ovid_agent_id UUID;
    ovid_prop_id UUID;
BEGIN
    -- ========== AYAT ==========
    INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, status, email_verified)
    VALUES (
        gen_random_uuid(),
        'ayat.realestate@example.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Ayat',
        'Real Estate',
        '+251911000001',
        'ACTIVE',
        true
    )
    RETURNING id INTO u_id;

    INSERT INTO user_roles (user_id, role) VALUES (u_id, 'REALTOR');

    INSERT INTO organizations (id, name, registration_number, type, status, address, city, country, phone_number, email)
    VALUES (
        gen_random_uuid(),
        'Ayat Real Estate',
        'REG-AYAT-001',
        'REAL_ESTATE_COMPANY',
        'APPROVED',
        'Bole Road',
        'Addis Ababa',
        'Ethiopia',
        '+251911000001',
        'ayat.realestate@example.com'
    )
    RETURNING id INTO ayat_org_id;

    INSERT INTO real_estate_agents (id, user_id, organization_id, status, license_number, is_super_agent)
    VALUES (gen_random_uuid(), u_id, ayat_org_id, 'ACTIVE', 'AYAT-LIC-001', true)
    RETURNING id INTO ayat_agent_id;

    INSERT INTO properties (
        id, title, description, type, status, verification_status, price, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id, category
    ) VALUES (
        gen_random_uuid(),
        'Modern Apartment in Bole – Ayat',
        'Spacious modern apartment with city views. Quality finishes and convenient location.',
        'APARTMENT',
        'AVAILABLE',
        'VERIFIED',
        3200000.00,
        3200000.00,
        'Bole Road, Near Edna Mall',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.005401,
        38.763611,
        2,
        2,
        95.0,
        5,
        10,
        ayat_org_id,
        'READY_TO_MOVE',
        ayat_agent_id,
        'FOR_SALE'
    )
    RETURNING id INTO ayat_prop_id;

    INSERT INTO media_attachments (id, organization_id, property_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ayat_org_id, NULL, '/realestates/ayat/ayat logo.png', 'ayat logo.png', 'image/png', 0, true, 'LOGO');

    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ayat_prop_id, NULL, '/realestates/ayat/ayat pic.jpeg', 'ayat pic.jpeg', 'image/jpeg', 0, true, 'IMAGE');
    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ayat_prop_id, NULL, '/realestates/ayat/ayat.jpeg', 'ayat.jpeg', 'image/jpeg', 1, false, 'IMAGE');
    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ayat_prop_id, NULL, '/realestates/ayat/aayat.jpeg', 'aayat.jpeg', 'image/jpeg', 2, false, 'IMAGE');

    -- ========== TEMER ==========
    INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, status, email_verified)
    VALUES (
        gen_random_uuid(),
        'temer.realestate@example.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Temer',
        'Real Estate',
        '+251911000002',
        'ACTIVE',
        true
    )
    RETURNING id INTO u_id;

    INSERT INTO user_roles (user_id, role) VALUES (u_id, 'REALTOR');

    INSERT INTO organizations (id, name, registration_number, type, status, address, city, country, phone_number, email)
    VALUES (
        gen_random_uuid(),
        'Temer Real Estate',
        'REG-TEMER-001',
        'REAL_ESTATE_COMPANY',
        'APPROVED',
        'Kazanchis',
        'Addis Ababa',
        'Ethiopia',
        '+251911000002',
        'temer.realestate@example.com'
    )
    RETURNING id INTO temer_org_id;

    INSERT INTO real_estate_agents (id, user_id, organization_id, status, license_number, is_super_agent)
    VALUES (gen_random_uuid(), u_id, temer_org_id, 'ACTIVE', 'TEMER-LIC-001', true)
    RETURNING id INTO temer_agent_id;

    INSERT INTO properties (
        id, title, description, type, status, verification_status, price, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id, category
    ) VALUES (
        gen_random_uuid(),
        'Family House in Kazanchis – Temer',
        'Well-maintained family house with garden. Quiet neighborhood, close to schools and amenities.',
        'HOUSE',
        'AVAILABLE',
        'VERIFIED',
        4500000.00,
        4500000.00,
        'Kazanchis, Near UNECA',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.023456,
        38.745678,
        3,
        2,
        140.0,
        1,
        2,
        temer_org_id,
        'READY_TO_MOVE',
        temer_agent_id,
        'FOR_SALE'
    )
    RETURNING id INTO temer_prop_id;

    INSERT INTO media_attachments (id, organization_id, property_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), temer_org_id, NULL, '/realestates/temer/temer logo.png', 'temer logo.png', 'image/png', 0, true, 'LOGO');

    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), temer_prop_id, NULL, '/realestates/temer/pic temer.jpeg', 'pic temer.jpeg', 'image/jpeg', 0, true, 'IMAGE');
    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), temer_prop_id, NULL, '/realestates/temer/temer pi.jpeg', 'temer pi.jpeg', 'image/jpeg', 1, false, 'IMAGE');
    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), temer_prop_id, NULL, '/realestates/temer/tem hdh.jpeg', 'tem hdh.jpeg', 'image/jpeg', 2, false, 'IMAGE');

    -- ========== METROPOLITAN ==========
    INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, status, email_verified)
    VALUES (
        gen_random_uuid(),
        'metropolitan.realestate@example.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Metropolitan',
        'Real Estate',
        '+251911000003',
        'ACTIVE',
        true
    )
    RETURNING id INTO u_id;

    INSERT INTO user_roles (user_id, role) VALUES (u_id, 'REALTOR');

    INSERT INTO organizations (id, name, registration_number, type, status, address, city, country, phone_number, email)
    VALUES (
        gen_random_uuid(),
        'Metropolitan Real Estate',
        'REG-METRO-001',
        'REAL_ESTATE_COMPANY',
        'APPROVED',
        'CMC Area',
        'Addis Ababa',
        'Ethiopia',
        '+251911000003',
        'metropolitan.realestate@example.com'
    )
    RETURNING id INTO metro_org_id;

    INSERT INTO real_estate_agents (id, user_id, organization_id, status, license_number, is_super_agent)
    VALUES (gen_random_uuid(), u_id, metro_org_id, 'ACTIVE', 'METRO-LIC-001', true)
    RETURNING id INTO metro_agent_id;

    INSERT INTO properties (
        id, title, description, type, status, verification_status, price, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id, category
    ) VALUES (
        gen_random_uuid(),
        'Metropolitan Tower Apartment',
        'Premium apartment in the city''s most connected location. Nearly sold out – don''t miss your address of prestige.',
        'APARTMENT',
        'AVAILABLE',
        'VERIFIED',
        5800000.00,
        5800000.00,
        'CMC Area, Metropolitan Tower',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.012345,
        38.789012,
        3,
        3,
        120.0,
        8,
        15,
        metro_org_id,
        'UNDER_CONSTRUCTION',
        metro_agent_id,
        'FOR_SALE'
    )
    RETURNING id INTO metro_prop_id;

    INSERT INTO media_attachments (id, organization_id, property_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), metro_org_id, NULL, '/realestates/metropolitan/Blog_company_profile-03.jpg', 'Blog_company_profile-03.jpg', 'image/jpeg', 0, true, 'LOGO');

    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), metro_prop_id, NULL, '/realestates/metropolitan/WhatsApp-Image-2025-07-16-at-15.15.08-1-1536x1024.jpeg', 'WhatsApp-Image-2025-07-16-at-15.15.08-1-1536x1024.jpeg', 'image/jpeg', 0, true, 'IMAGE');
    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), metro_prop_id, NULL, '/realestates/metropolitan/Blog_company_profile-03.jpg', 'Blog_company_profile-03.jpg', 'image/jpeg', 1, false, 'IMAGE');

    -- ========== OVID ==========
    INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, status, email_verified)
    VALUES (
        gen_random_uuid(),
        'ovid.realestate@example.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Ovid',
        'Real Estate',
        '+251911000004',
        'ACTIVE',
        true
    )
    RETURNING id INTO u_id;

    INSERT INTO user_roles (user_id, role) VALUES (u_id, 'REALTOR');

    INSERT INTO organizations (id, name, registration_number, type, status, address, city, country, phone_number, email)
    VALUES (
        gen_random_uuid(),
        'Ovid Real Estate',
        'REG-OVID-001',
        'REAL_ESTATE_COMPANY',
        'APPROVED',
        'Gerji',
        'Addis Ababa',
        'Ethiopia',
        '+251911000004',
        'ovid.realestate@example.com'
    )
    RETURNING id INTO ovid_org_id;

    INSERT INTO real_estate_agents (id, user_id, organization_id, status, license_number, is_super_agent)
    VALUES (gen_random_uuid(), u_id, ovid_org_id, 'ACTIVE', 'OVID-LIC-001', true)
    RETURNING id INTO ovid_agent_id;

    INSERT INTO properties (
        id, title, description, type, status, verification_status, price, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id, category
    ) VALUES (
        gen_random_uuid(),
        'Contemporary Villa in Gerji – Ovid',
        'Stunning contemporary villa with premium finishes. Private garden and modern layout.',
        'VILLA',
        'AVAILABLE',
        'VERIFIED',
        7200000.00,
        7200000.00,
        'Gerji Area',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.034567,
        38.801234,
        4,
        4,
        220.0,
        1,
        2,
        ovid_org_id,
        'READY_TO_MOVE',
        ovid_agent_id,
        'FOR_SALE'
    )
    RETURNING id INTO ovid_prop_id;

    INSERT INTO media_attachments (id, organization_id, property_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ovid_org_id, NULL, '/realestates/ovid/ovid logo.jpeg', 'ovid logo.jpeg', 'image/jpeg', 0, true, 'LOGO');

    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ovid_prop_id, NULL, '/realestates/ovid/ovid p.jpeg', 'ovid p.jpeg', 'image/jpeg', 0, true, 'IMAGE');
    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ovid_prop_id, NULL, '/realestates/ovid/ovid pic.jpeg', 'ovid pic.jpeg', 'image/jpeg', 1, false, 'IMAGE');
    INSERT INTO media_attachments (id, property_id, organization_id, image_url, file_name, content_type, display_order, is_primary, media_kind)
    VALUES (gen_random_uuid(), ovid_prop_id, NULL, '/realestates/ovid/ovid pict.jpeg', 'ovid pict.jpeg', 'image/jpeg', 2, false, 'IMAGE');

    RAISE NOTICE 'Inserted sample real estates: Ayat, Temer, Metropolitan, Ovid (with logos and property images from realestates folder)';
END $$;
