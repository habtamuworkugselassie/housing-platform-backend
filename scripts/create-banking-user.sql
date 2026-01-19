-- Script to create a banking user and bank organization
-- This script creates a sample banker user and bank organization for testing
-- Usage: psql -U housing_platform -d housing_platform -f scripts/create-banking-user.sql

DO $$
DECLARE
    banker_user_id UUID;
    bank_org_id UUID;
    user_email TEXT := 'banker@example.com';
    user_password_hash TEXT := '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'; -- password: password123
    bank_name TEXT := 'Sample Bank';
    bank_reg_number TEXT := 'BANK-001';
BEGIN
    -- Check if banker user already exists
    SELECT id INTO banker_user_id 
    FROM users 
    WHERE email = user_email 
    LIMIT 1;
    
    IF banker_user_id IS NULL THEN
        -- Create the banker user
        INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, status, email_verified, phone_verified)
        VALUES (
            gen_random_uuid(),
            user_email,
            user_password_hash,
            'John',
            'Banker',
            '+251911234567',
            'ACTIVE',
            true,
            true
        )
        RETURNING id INTO banker_user_id;
        
        -- Add BANKER role
        INSERT INTO user_roles (user_id, role)
        VALUES (banker_user_id, 'BANKER');
        
        RAISE NOTICE 'Created banker user with ID: % and email: %', banker_user_id, user_email;
    ELSE
        RAISE NOTICE 'Banker user already exists with ID: % and email: %', banker_user_id, user_email;
    END IF;
    
    -- Check if bank organization already exists
    SELECT id INTO bank_org_id 
    FROM organizations 
    WHERE name = bank_name 
    LIMIT 1;
    
    IF bank_org_id IS NULL THEN
        -- Create the bank organization
        INSERT INTO organizations (
            id, name, registration_number, type, status, 
            address, city, country, phone_number, email, website, description,
            primary_contact_user_id
        )
        VALUES (
            gen_random_uuid(),
            bank_name,
            bank_reg_number,
            'BANK',
            'APPROVED',
            '123 Banking Street',
            'Addis Ababa',
            'Ethiopia',
            '+251111234567',
            'info@samplebank.com',
            'https://www.samplebank.com',
            'A leading financial institution providing housing loans and financing solutions.',
            banker_user_id
        )
        RETURNING id INTO bank_org_id;
        
        RAISE NOTICE 'Created bank organization with ID: % and name: %', bank_org_id, bank_name;
    ELSE
        -- Update existing bank to link to banker user if not already linked
        UPDATE organizations 
        SET primary_contact_user_id = banker_user_id
        WHERE id = bank_org_id AND primary_contact_user_id IS NULL;
        
        RAISE NOTICE 'Bank organization already exists with ID: % and name: %', bank_org_id, bank_name;
    END IF;
    
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Banking User Setup Complete!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'User Email: %', user_email;
    RAISE NOTICE 'Password: password123';
    RAISE NOTICE 'User ID: %', banker_user_id;
    RAISE NOTICE 'Bank Name: %', bank_name;
    RAISE NOTICE 'Bank ID: %', bank_org_id;
    RAISE NOTICE '========================================';
    
END $$;

-- Display created banking user and organization
SELECT 
    u.id as user_id,
    u.email,
    u.first_name,
    u.last_name,
    u.status as user_status,
    ur.role,
    o.id as bank_id,
    o.name as bank_name,
    o.registration_number,
    o.status as bank_status,
    o.email as bank_email
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN organizations o ON o.primary_contact_user_id = u.id AND o.type = 'BANK'
WHERE u.email = 'banker@example.com'
ORDER BY u.created_at DESC;