-- Script to create sample credit products and financing offers
-- This script creates sample credit products for the bank and links them to properties/buildings
-- Usage: psql -U housing_platform -d housing_platform -f scripts/create-sample-credit-products.sql

DO $$
DECLARE
    bank_org_id UUID;
    credit_product_1_id UUID;
    credit_product_2_id UUID;
    credit_product_3_id UUID;
    sample_property_id UUID;
    sample_building_id UUID;
    financing_offer_1_id UUID;
    financing_offer_2_id UUID;
    financing_offer_3_id UUID;
    financing_offer_4_id UUID;
    real_estate_company_id UUID;
BEGIN
    -- Get the bank organization (created by create-banking-user.sql)
    SELECT id INTO bank_org_id 
    FROM organizations 
    WHERE type = 'BANK' AND name = 'Sample Bank'
    LIMIT 1;
    
    IF bank_org_id IS NULL THEN
        RAISE EXCEPTION 'Bank organization not found. Please run create-banking-user.sql first.';
    END IF;
    
    RAISE NOTICE 'Found bank organization with ID: %', bank_org_id;
    
    -- Create Credit Product 1: Home Purchase Loan
    INSERT INTO credit_products (
        bank_id, name, description, product_type, interest_rate,
        min_tenure_months, max_tenure_months, max_loan_to_value_ratio,
        min_loan_amount, max_loan_amount, eligibility_criteria, status,
        processing_fee, prepayment_penalty
    )
    VALUES (
        bank_org_id,
        'Home Purchase Loan',
        'Flexible home purchase financing with competitive interest rates. Perfect for first-time homebuyers and property investors.',
        'HOME_PURCHASE',
        8.50,
        12,
        240,
        0.80,
        50000.00,
        5000000.00,
        'Minimum income: ETB 10,000/month, Credit score: 650+, Employment: Minimum 2 years',
        'ACTIVE',
        5000.00,
        0.02
    )
    RETURNING id INTO credit_product_1_id;
    
    RAISE NOTICE 'Created credit product 1 (Home Purchase Loan) with ID: %', credit_product_1_id;
    
    -- Create Credit Product 2: Construction Loan
    INSERT INTO credit_products (
        bank_id, name, description, product_type, interest_rate,
        min_tenure_months, max_tenure_months, max_loan_to_value_ratio,
        min_loan_amount, max_loan_amount, eligibility_criteria, status,
        processing_fee, prepayment_penalty
    )
    VALUES (bank_org_id,
        'Construction Loan',
        'Specialized financing for building new homes. Progressive disbursement based on construction milestones.',
        'CONSTRUCTION_LOAN',
        9.25,
        18,
        300,
        0.75,              -- ✅ LTV
        10000000.00,
        10000000.00,
        'Land ownership required, Construction plan approval needed, Minimum 20% equity',
        'ACTIVE',
        10000.00,
        0.03
    )
    RETURNING id INTO credit_product_2_id;
    
    RAISE NOTICE 'Created credit product 2 (Construction Loan) with ID: %', credit_product_2_id;
    
    -- Create Credit Product 3: Material Financing
    INSERT INTO credit_products (
        bank_id, name, description, product_type, interest_rate,
        min_tenure_months, max_tenure_months, max_loan_to_value_ratio,
        min_loan_amount, max_loan_amount, eligibility_criteria, status,
        processing_fee, prepayment_penalty
    )
    VALUES (
        bank_org_id,
        'Material Financing',
        'Quick financing for construction materials. Fast approval and flexible repayment terms.',
        'MATERIAL_FINANCING',
        10.00,
        6,
        36,
        0.75,              -- ✅ LTV
        2000000.00,
        2000000.00,
        'Active construction project, Material supplier quotes required',
        'ACTIVE',
        2000.00,
        0.01
    )
    RETURNING id INTO credit_product_3_id;
    
    RAISE NOTICE 'Created credit product 3 (Material Financing) with ID: %', credit_product_3_id;
    
    -- Get or create a sample property
    SELECT id INTO sample_property_id 
    FROM properties 
    WHERE status = 'AVAILABLE'
    LIMIT 1;
    
    IF sample_property_id IS NULL THEN
        -- Get a real estate company for the property (reuse the one we'll use for building)
        DECLARE
            property_company_id UUID;
        BEGIN
            SELECT id INTO property_company_id 
            FROM organizations 
            WHERE type = 'REAL_ESTATE_COMPANY'
            LIMIT 1;
            
            IF property_company_id IS NULL THEN
                -- Create a sample real estate company if it doesn't exist
                INSERT INTO organizations (
                    name, registration_number, type, status,
                    address, city, country
                )
                VALUES (
                    'Sample Real Estate Company',
                    'RE-001',
                    'REAL_ESTATE_COMPANY',
                    'APPROVED',
                    '123 Real Estate Street',
                    'Addis Ababa',
                    'Ethiopia'
                )
                RETURNING id INTO property_company_id;
            END IF;
            
            -- Create a sample property if none exists
            INSERT INTO properties (
                title, description, type, status, verification_status, price,
                address, city, country, bedrooms, bathrooms, area,
                construction_status, category, construction_percentage, is_fully_furnished,
                real_estate_company_id
            )
            VALUES (
                'Modern 3 Bedroom Apartment in Bole',
                'Beautiful modern apartment with great amenities',
                'APARTMENT',
                'AVAILABLE',
                'VERIFIED',
                2500000.00,
                'Bole Sub-city, Addis Ababa',
                'Addis Ababa',
                'Ethiopia',
                3,
                2,
                120.00,
                'READY_TO_MOVE',
                'FOR_SALE',
                100,
                true,
                property_company_id
            )
            RETURNING id INTO sample_property_id;
            
            RAISE NOTICE 'Created sample property with ID: %', sample_property_id;
        END;
    ELSE
        RAISE NOTICE 'Using existing property with ID: %', sample_property_id;
    END IF;
    
    -- Get or create a sample building
    SELECT id INTO sample_building_id 
    FROM buildings 
    LIMIT 1;
    
    IF sample_building_id IS NULL THEN
        -- Get a real estate company for the building
        SELECT id INTO real_estate_company_id 
        FROM organizations 
        WHERE type = 'REAL_ESTATE_COMPANY'
        LIMIT 1;
        
        IF real_estate_company_id IS NULL THEN
            -- Create a sample real estate company
            INSERT INTO organizations (
                name, registration_number, type, status,
                address, city, country
            )
            VALUES (
                'Sample Real Estate Company',
                'RE-001',
                'REAL_ESTATE_COMPANY',
                'APPROVED',
                '123 Real Estate Street',
                'Addis Ababa',
                'Ethiopia'
            )
            RETURNING id INTO real_estate_company_id;
        END IF;
        
        -- Create a sample building
        INSERT INTO buildings (
            name, description, address, city, country,
            total_floors, total_units, real_estate_company_id,
            building_type, status, category, construction_percentage, is_fully_furnished
        )
        VALUES (
            'Luxury Apartment Complex',
            'Modern luxury apartment complex with premium amenities',
            'Bole Sub-city, Addis Ababa',
            'Addis Ababa',
            'Ethiopia',
            10,
            50,
            real_estate_company_id,
            'APARTMENT_COMPLEX',
            'COMPLETED',
            'FOR_SALE',
            100,
            true
        )
        RETURNING id INTO sample_building_id;
        
        RAISE NOTICE 'Created sample building with ID: %', sample_building_id;
    ELSE
        RAISE NOTICE 'Using existing building with ID: %', sample_building_id;
    END IF;
    
    -- Create Financing Offer 1: Home Purchase Loan linked to Property
    INSERT INTO financing_offers (
        bank_id, credit_product_id, property_id, building_id, project_id,
        special_interest_rate, special_ltv_ratio, special_terms, status,
        approval_notes
    )
    VALUES (
        bank_org_id,
        credit_product_1_id,
        sample_property_id,
        NULL,
        NULL,
        8.00,  -- Special lower interest rate
        0.85,  -- Special higher LTV ratio
        'Special promotion: Reduced interest rate and higher LTV for this property. Valid until end of year.',
        'ACTIVE',
        'Approved for special promotion'
    )
    RETURNING id INTO financing_offer_1_id;
    
    RAISE NOTICE 'Created financing offer 1 (Home Purchase - Property) with ID: %', financing_offer_1_id;
    
    -- Create Financing Offer 2: Construction Loan linked to Building
    INSERT INTO financing_offers (
        bank_id, credit_product_id, property_id, building_id, project_id,
        special_interest_rate, special_ltv_ratio, special_terms, status,
        approval_notes
    )
    VALUES (
        bank_org_id,
        credit_product_2_id,
        NULL,
        sample_building_id,
        NULL,
        9.00,  -- Special lower interest rate
        0.75,  -- Standard LTV
        'Exclusive financing for units in this building. Progressive disbursement available.',
        'ACTIVE',
        'Approved for building financing'
    )
    RETURNING id INTO financing_offer_2_id;
    
    RAISE NOTICE 'Created financing offer 2 (Construction Loan - Building) with ID: %', financing_offer_2_id;
    
    -- Create Financing Offer 3: Material Financing linked to Building
    INSERT INTO financing_offers (
        bank_id, credit_product_id, property_id, building_id, project_id,
        special_interest_rate, special_ltv_ratio, special_terms, status,
        approval_notes
    )
    VALUES (
        bank_org_id,
        credit_product_3_id,
        NULL,
        sample_building_id,
        NULL,
        NULL,  -- Use product default rate
        NULL,  -- Use product default LTV
        'Fast-track approval for material financing. Same-day disbursement available.',
        'ACTIVE',
        'Approved for material financing'
    )
    RETURNING id INTO financing_offer_3_id;
    
    RAISE NOTICE 'Created financing offer 3 (Material Financing - Building) with ID: %', financing_offer_3_id;
    
    -- Create Financing Offer 4: General Home Purchase Loan (no property/building link)
    INSERT INTO financing_offers (
        bank_id, credit_product_id, property_id, building_id, project_id,
        special_interest_rate, special_ltv_ratio, special_terms, status,
        approval_notes
    )
    VALUES (
        bank_org_id,
        credit_product_1_id,
        NULL,
        NULL,
        NULL,
        NULL,  -- Use product default rate
        NULL,  -- Use product default LTV
        'General financing offer available for all eligible properties. Contact bank for details.',
        'ACTIVE',
        'General offer approved'
    )
    RETURNING id INTO financing_offer_4_id;
    
    RAISE NOTICE 'Created financing offer 4 (General Home Purchase) with ID: %', financing_offer_4_id;
    
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Sample Credit Products and Financing Offers Created!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Bank ID: %', bank_org_id;
    RAISE NOTICE 'Credit Products Created: 3';
    RAISE NOTICE '  - Home Purchase Loan: %', credit_product_1_id;
    RAISE NOTICE '  - Construction Loan: %', credit_product_2_id;
    RAISE NOTICE '  - Material Financing: %', credit_product_3_id;
    RAISE NOTICE 'Financing Offers Created: 4';
    RAISE NOTICE '  - Home Purchase (Property): %', financing_offer_1_id;
    RAISE NOTICE '  - Construction Loan (Building): %', financing_offer_2_id;
    RAISE NOTICE '  - Material Financing (Building): %', financing_offer_3_id;
    RAISE NOTICE '  - General Home Purchase: %', financing_offer_4_id;
    RAISE NOTICE 'Property Used: %', sample_property_id;
    RAISE NOTICE 'Building Used: %', sample_building_id;
    RAISE NOTICE '========================================';
    
END $$;

-- Display created credit products
SELECT 
    cp.id,
    cp.name,
    cp.product_type,
    cp.interest_rate,
    cp.min_loan_amount,
    cp.max_loan_amount,
    cp.status,
    o.name as bank_name
FROM credit_products cp
JOIN organizations o ON cp.bank_id = o.id
WHERE o.name = 'Sample Bank'
ORDER BY cp.created_at DESC;

-- Display created financing offers
SELECT 
    fo.id,
    fo.status,
    cp.name as credit_product_name,
    CASE 
        WHEN fo.property_id IS NOT NULL THEN 'Property'
        WHEN fo.building_id IS NOT NULL THEN 'Building'
        ELSE 'General'
    END as link_type,
    fo.property_id,
    fo.building_id,
    fo.special_interest_rate,
    fo.special_ltv_ratio
FROM financing_offers fo
JOIN credit_products cp ON fo.credit_product_id = cp.id
JOIN organizations o ON fo.bank_id = o.id
WHERE o.name = 'Sample Bank'
ORDER BY fo.created_at DESC;
