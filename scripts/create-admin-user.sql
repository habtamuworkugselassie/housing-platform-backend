-- Script to create an admin user
-- This script creates a default admin user with ADMIN role
-- Default credentials: admin@housingplatform.com / admin123
-- 
-- Usage:
--   psql -U your_username -d your_database -f create-admin-user.sql
--   Or execute in your database client

DO $$
DECLARE
    admin_user_id UUID;
    -- Default password: admin123
    -- BCrypt hash for "admin123" (cost factor 10)
    -- You can generate a new hash using: BCryptPasswordEncoder().encode("your_password")
    password_hash VARCHAR(255) := '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
BEGIN
    -- Check if admin user already exists
    IF EXISTS (SELECT 1 FROM users WHERE email = 'admin@housingplatform.com') THEN
        RAISE NOTICE 'Admin user already exists. Skipping creation.';
    ELSE
        -- Generate UUID for admin user
        admin_user_id := gen_random_uuid();
        
        -- Insert admin user
        INSERT INTO users (
            id,
            email,
            password_hash,
            first_name,
            last_name,
            phone_number,
            status,
            email_verified,
            phone_verified,
            created_at,
            updated_at,
            version
        ) VALUES (
            admin_user_id,
            'admin@housingplatform.com',
            password_hash,
            'Admin',
            'User',
            NULL,
            'ACTIVE',
            TRUE,
            FALSE,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        );
        
        -- Insert ADMIN role
        INSERT INTO user_roles (user_id, role) VALUES (admin_user_id, 'ADMIN');
        
        RAISE NOTICE 'Admin user created successfully!';
        RAISE NOTICE 'User ID: %', admin_user_id;
        RAISE NOTICE 'Email: admin@housingplatform.com';
        RAISE NOTICE 'Password: admin123';
        RAISE NOTICE '';
        RAISE NOTICE 'IMPORTANT: Change the password after first login!';
    END IF;
END $$;

-- Verify the admin user was created
SELECT 
    u.id,
    u.email,
    u.first_name,
    u.last_name,
    u.status,
    u.email_verified,
    array_agg(ur.role) as roles
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
WHERE u.email = 'admin@housingplatform.com'
GROUP BY u.id, u.email, u.first_name, u.last_name, u.status, u.email_verified;
