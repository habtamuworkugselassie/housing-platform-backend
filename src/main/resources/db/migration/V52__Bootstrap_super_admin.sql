-- Bootstrap a SUPER_ADMIN account with a known password.
--
-- SUPER_ADMIN mints both the `admin` and `super_admin` token scopes
-- (AuthenticationServiceImpl.mapRoleToScopes), so the ADMIN role is not needed as well.
-- It is the tier that issues sponsor-company logins at Admin -> Organizations -> Accounts.
--
-- A dedicated address is used rather than the admin@housingplatform.com that
-- scripts/create-admin-user.sql creates, so this cannot collide with an account already in
-- use. The password is set on every run, not only on insert, so the credential is
-- deterministic even if the row is recreated.
--
-- The hash was produced with Spring's own BCryptPasswordEncoder at cost 10 — the same class
-- the application verifies with — and confirmed with matches() before committing. The
-- password also satisfies SetAccountPasswordRequest's own pattern, so it can be re-entered
-- through the UI.
--
-- The hash is permanent in git history and Flyway replays this on every environment, so
-- change the password once the account is in use. For routine provisioning prefer
-- scripts/create-super-admin.sh, which writes straight to the database and commits nothing.

DO $$
DECLARE
    v_email TEXT := 'superadmin@ethiobuildconnect.et';
    -- Password@1212
    v_hash  TEXT := '$2a$10$HpHy57IqiPDrIYMeBTuWF..Pbj/jKmRSnwdGCHh5GfhWeQn0KJrdy';

    v_first TEXT := 'Super';
    v_last  TEXT := 'Admin';
    v_id    UUID;
BEGIN
    v_email := lower(trim(v_email));

    SELECT id INTO v_id FROM users WHERE email = v_email;

    IF v_id IS NULL THEN
        INSERT INTO users (
            id, email, password_hash, first_name, last_name,
            status, email_verified, phone_verified,
            created_at, updated_at, version, created_by, updated_by
        )
        VALUES (
            gen_random_uuid(), v_email, v_hash, v_first, v_last,
            'ACTIVE', TRUE, FALSE,
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V52', 'V52'
        )
        RETURNING id INTO v_id;

        RAISE NOTICE 'V52: created super admin %', v_email;
    ELSE
        -- Keep the credential deterministic, and make sure the account can actually sign in:
        -- a PENDING_VERIFICATION or disabled status would leave it locked out despite the role.
        UPDATE users
        SET password_hash = v_hash,
            status        = 'ACTIVE',
            updated_at    = CURRENT_TIMESTAMP,
            updated_by    = 'V52'
        WHERE id = v_id;

        RAISE NOTICE 'V52: reset password and granted SUPER_ADMIN to existing account %', v_email;
    END IF;

    INSERT INTO user_roles (user_id, role)
    VALUES (v_id, 'SUPER_ADMIN')
    ON CONFLICT DO NOTHING;
END $$;
