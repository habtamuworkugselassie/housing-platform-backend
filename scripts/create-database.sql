-- SQL script to create PostgreSQL database and user for Housing Platform
-- Usage: psql -U postgres -f scripts/create-database.sql
-- Migration: create_database.sql
-- Description: Create the housing_platform database
-- Author: Development Team
-- Date: Initial database setup
--
-- NOTE: This script should be run manually by connecting to the default 'postgres' database
-- before running Flyway migrations. Flyway migrations run within a database connection,
-- so they cannot create the database they are connecting to.
--
-- Usage:
--   1. Connect to PostgreSQL: psql -U postgres -d postgres
--   2. Run this script: \i src/main/resources/db/migration/create_database.sql
--   OR
--   psql -U postgres -d postgres -f src/main/resources/db/migration/create_database.sql

-- Check if database exists, if not create it
DO $$
    DECLARE
        _db TEXT := 'housing_platform';
        _user TEXT := 'housing_platform';
        _password TEXT := 'housing_platform';
    BEGIN
        CREATE EXTENSION IF NOT EXISTS dblink; -- enable extension
        IF EXISTS (SELECT 1 FROM pg_database WHERE datname = _db) THEN
            RAISE NOTICE 'Database already exists';
        ELSE
            CREATE DATABASE housing_platform;
        END IF;
        IF NOT EXISTS (
            SELECT FROM pg_roles WHERE rolname = 'housing_platform'
        ) THEN
            CREATE ROLE housing_platform
                LOGIN
                PASSWORD 'housing_platform';
        END IF;
    END$$;

GRANT ALL PRIVILEGES ON DATABASE housing_platform TO housing_platform;

ALTER SCHEMA public OWNER TO housing_platform;

-- Grant extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

-- Connect to the new database and grant schema privileges
\c housing_platform

GRANT ALL ON SCHEMA public TO housing_platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO housing_platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO housing_platform;

-- Display success message
\echo 'Database and user created successfully!'
\echo 'Database: housing_platform'
\echo 'User: housing_platform'
\echo 'Password: housing_platform'
