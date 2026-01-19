-- Script to insert sample properties into the database
-- This script creates sample organizations, agents, and properties for testing

-- First, let's create a sample real estate company if it doesn't exist
DO $$
DECLARE
    sample_org_id UUID;
    sample_user_id UUID;
    sample_agent_id UUID;
BEGIN
    -- Check if sample organization exists, if not create it
    SELECT id INTO sample_org_id 
    FROM organizations 
    WHERE name = 'Sample Real Estate Company' 
    LIMIT 1;
    
    IF sample_org_id IS NULL THEN
        -- Create a sample user for the organization
        INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, status, email_verified)
        VALUES (
            gen_random_uuid(),
            'sample.realtor@example.com',
            '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- password: password123
            'John',
            'Realtor',
            '+1234567890',
            'ACTIVE',
            true
        )
        RETURNING id INTO sample_user_id;
        
        -- Add REALTOR role
        INSERT INTO user_roles (user_id, role)
        VALUES (sample_user_id, 'REALTOR');
        
        -- Create the organization
        INSERT INTO organizations (id, name, registration_number, type, status, address, city, country, phone_number, email, primary_contact_user_id)
        VALUES (
            gen_random_uuid(),
            'Sample Real Estate Company',
            'REG-001',
            'REAL_ESTATE_COMPANY',
            'APPROVED',
            '123 Main Street',
            'Addis Ababa',
            'Ethiopia',
            '+1234567890',
            'info@samplerealestate.com',
            sample_user_id
        )
        RETURNING id INTO sample_org_id;
        
        -- Create a real estate agent
        INSERT INTO real_estate_agents (id, user_id, organization_id, status, license_number, is_super_agent)
        VALUES (
            gen_random_uuid(),
            sample_user_id,
            sample_org_id,
            'ACTIVE',
            'LIC-001',
            true
        )
        RETURNING id INTO sample_agent_id;
        
        RAISE NOTICE 'Created sample organization with ID: %', sample_org_id;
        RAISE NOTICE 'Created sample agent with ID: %', sample_agent_id;
    ELSE
        -- Get existing organization and agent
        SELECT id INTO sample_agent_id
        FROM real_estate_agents
        WHERE organization_id = sample_org_id
        LIMIT 1;
        
        RAISE NOTICE 'Using existing organization with ID: %', sample_org_id;
        RAISE NOTICE 'Using existing agent with ID: %', sample_agent_id;
    END IF;
    
    -- Now insert sample properties
    -- Property 1: Modern Apartment
    INSERT INTO properties (
        id, title, description, type, status, verification_status, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id
    ) VALUES (
        gen_random_uuid(),
        'Modern 2BR Apartment in Bole',
        'Beautiful modern apartment with stunning city views. Features include spacious living room, fully equipped kitchen, and balcony. Located in the heart of Bole with easy access to shopping centers and restaurants.',
        'APARTMENT',
        'AVAILABLE',
        'VERIFIED',
        2500000.00,
        'Bole Road, Near Edna Mall',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.005401,
        38.763611,
        2,
        2,
        85.5,
        5,
        10,
        sample_org_id,
        'READY_TO_MOVE',
        sample_agent_id
    );
    
    -- Property 2: Luxury Villa
    INSERT INTO properties (
        id, title, description, type, status, verification_status, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id
    ) VALUES (
        gen_random_uuid(),
        'Luxury 4BR Villa in CMC',
        'Stunning luxury villa with private garden and swimming pool. Features include master suite with walk-in closet, modern kitchen with island, home office, and entertainment area. Perfect for families seeking luxury living.',
        'VILLA',
        'AVAILABLE',
        'VERIFIED',
        8500000.00,
        'CMC Area, Near CMC Hospital',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.012345,
        38.789012,
        4,
        4,
        350.0,
        1,
        2,
        sample_org_id,
        'READY_TO_MOVE',
        sample_agent_id
    );
    
    -- Property 3: Townhouse
    INSERT INTO properties (
        id, title, description, type, status, verification_status, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id
    ) VALUES (
        gen_random_uuid(),
        'Cozy 3BR Townhouse in Kazanchis',
        'Well-maintained townhouse in a quiet neighborhood. Features include private parking, small garden, and modern finishes throughout. Close to schools, hospitals, and shopping areas.',
        'TOWNHOUSE',
        'AVAILABLE',
        'VERIFIED',
        3200000.00,
        'Kazanchis, Near UNECA',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.023456,
        38.745678,
        3,
        2,
        120.0,
        1,
        2,
        sample_org_id,
        'READY_TO_MOVE',
        sample_agent_id
    );
    
    -- Property 4: Land for Sale
    INSERT INTO properties (
        id, title, description, type, status, verification_status, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area,
        real_estate_company_id, construction_status, agent_id
    ) VALUES (
        gen_random_uuid(),
        'Prime Land in Gerji Area',
        'Prime residential land in Gerji area, perfect for building your dream home. The land is fully serviced with water, electricity, and road access. Located in a rapidly developing area with great investment potential.',
        'LAND',
        'AVAILABLE',
        'VERIFIED',
        1500000.00,
        'Gerji Area, Near Bole Airport',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.034567,
        38.801234,
        NULL,
        NULL,
        500.0,
        sample_org_id,
        'PLANNED',
        sample_agent_id
    );
    
    -- Property 5: Condominium Under Construction
    INSERT INTO properties (
        id, title, description, type, status, verification_status, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id
    ) VALUES (
        gen_random_uuid(),
        'New 2BR Condominium in Piassa',
        'Brand new condominium unit in a modern building. Currently under construction with expected completion in 6 months. Features include modern design, parking space, and 24/7 security. Great investment opportunity.',
        'CONDOMINIUM',
        'AVAILABLE',
        'PENDING',
        1800000.00,
        'Piassa, Near National Theatre',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.045678,
        38.712345,
        2,
        1,
        65.0,
        3,
        8,
        sample_org_id,
        'UNDER_CONSTRUCTION',
        sample_agent_id
    );
    
    -- Property 6: Reserved House
    INSERT INTO properties (
        id, title, description, type, status, verification_status, price_etb,
        address, city, state, country, zip_code, latitude, longitude,
        bedrooms, bathrooms, area, floor_number, total_floors,
        real_estate_company_id, construction_status, agent_id
    ) VALUES (
        gen_random_uuid(),
        'Spacious 5BR House in Old Airport',
        'Large family house with beautiful garden and ample parking space. Features include multiple living areas, modern kitchen, and separate guest quarters. Ideal for large families.',
        'HOUSE',
        'RESERVED',
        'VERIFIED',
        5500000.00,
        'Old Airport Area',
        'Addis Ababa',
        'Addis Ababa',
        'Ethiopia',
        '1000',
        9.056789,
        38.823456,
        5,
        4,
        280.0,
        1,
        2,
        sample_org_id,
        'READY_TO_MOVE',
        sample_agent_id
    );
    
    RAISE NOTICE 'Successfully inserted 6 sample properties';
    
END $$;

-- Display inserted properties
SELECT 
    p.id,
    p.title,
    p.type,
    p.status,
    p.price_etb as price,
    p.city,
    p.bedrooms,
    p.bathrooms,
    p.area,
    o.name as company_name
FROM properties p
JOIN organizations o ON p.real_estate_company_id = o.id
WHERE o.name = 'Sample Real Estate Company'
ORDER BY p.created_at DESC;
