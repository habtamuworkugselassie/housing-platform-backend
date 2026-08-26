-- Create or promote a SUPER_ADMIN user.
--
-- SUPER_ADMIN is the tier that manages platform admins and issues sponsor-company logins
-- (Admin -> Organizations -> Accounts). It mints both the `admin` and `super_admin` token
-- scopes, so a super admin can reach every admin screen — the ADMIN role is not needed too.
--
-- This script is idempotent: run it again to reset the password or re-grant the role.
--
-- Required psql variables:
--   email          login email (case-insensitive; stored lowercased)
--   first_name     given name
--   last_name      family name
--   password_hash  BCrypt hash, or '' to promote an existing user without touching their password
--
-- Usage (prefer ./create-super-admin.sh, which generates the hash for you):
--   psql -U postgres -d housing_platform \
--        -v email=boss@example.com -v first_name=Ada -v last_name=Lovelace \
--        -v password_hash='$2a$10$...' \
--        -f create-super-admin.sql
--
-- To generate a hash by hand:
--   python3 -c "import bcrypt;print(bcrypt.hashpw(b'YourPassword1', bcrypt.gensalt(10)).decode())"
--   htpasswd -bnBC 10 "" 'YourPassword1' | tr -d ':\n'

\set ON_ERROR_STOP on

BEGIN;

-- 1. Create the account when the email is new. Skipped when no password was supplied,
--    because an account with an empty hash could never sign in.
INSERT INTO users (
    id, email, password_hash, first_name, last_name,
    status, email_verified, phone_verified,
    created_at, updated_at, version, created_by, updated_by
)
SELECT
    gen_random_uuid(), lower(trim(:'email')), :'password_hash', trim(:'first_name'), trim(:'last_name'),
    'ACTIVE', TRUE, FALSE,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'create-super-admin.sql', 'create-super-admin.sql'
WHERE :'password_hash' <> ''
  AND NOT EXISTS (SELECT 1 FROM users WHERE email = lower(trim(:'email')));

-- 2. Reset the password of an existing account, and make sure it can actually sign in
--    (a PENDING_VERIFICATION or disabled account would still be locked out).
UPDATE users
SET password_hash = :'password_hash',
    status        = 'ACTIVE',
    updated_at    = CURRENT_TIMESTAMP,
    updated_by    = 'create-super-admin.sql'
WHERE email = lower(trim(:'email'))
  AND :'password_hash' <> '';

-- 3. Grant the role. PK (user_id, role) makes the re-run a no-op.
INSERT INTO user_roles (user_id, role)
SELECT id, 'SUPER_ADMIN' FROM users WHERE email = lower(trim(:'email'))
ON CONFLICT DO NOTHING;

COMMIT;

-- 4. Show the result. An empty row here means the email did not exist and no password was
--    given, so nothing was created — re-run with a password to create the account.
SELECT
    u.id,
    u.email,
    u.first_name || ' ' || u.last_name AS name,
    u.status,
    array_agg(ur.role ORDER BY ur.role) AS roles
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
WHERE u.email = lower(trim(:'email'))
GROUP BY u.id, u.email, u.first_name, u.last_name, u.status;
