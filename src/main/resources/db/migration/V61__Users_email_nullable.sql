-- Quick registration lets a buyer sign up with a full name and a phone number only.
-- The UNIQUE constraint stays: PostgreSQL treats NULLs as distinct, so many phone-only users can coexist.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
