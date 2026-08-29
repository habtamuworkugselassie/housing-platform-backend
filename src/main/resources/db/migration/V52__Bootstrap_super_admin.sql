-- Bootstrap a SUPER_ADMIN account.
--
-- SUPER_ADMIN mints both the `admin` and `super_admin` token scopes
-- (AuthenticationServiceImpl.mapRoleToScopes), so the ADMIN role is not needed as well.
-- It is the tier that issues sponsor-company logins at Admin -> Organizations -> Accounts.
--
-- The hash below was produced with Spring's own BCryptPasswordEncoder at cost 10 — the same
-- class the application verifies with — and confirmed with matches() before committing.
-- Change v_email below if this is not the address you want.
--
-- SECURITY: this hash is permanent in git history, and Flyway replays the migration on every
-- environment, so anyone with repository access can sign in as super admin. Treat the
-- password as compromised the moment this is committed: sign in once, change it, and prefer
-- scripts/create-super-admin.sh in future — it writes straight to the database and commits
-- nothing.
--
-- Note also that this password does not satisfy the platform's own policy
-- (SetAccountPasswordRequest requires an uppercase letter), so it cannot be re-entered
-- through the UI later. Login itself does not re-validate, so signing in works.

DO $$
DECLARE
    v_email TEXT := 'superadmin@ethiobuildconnect.et';
    v_hash  TEXT := '$2a$10$hLgE/57rQzTn3wTfvW2Pa.QlXOH26H8Xs.WMehQPRJyxy8Jw/eidW';

    v_first TEXT := 'Super';
    v_last  TEXT := 'Admin';
    v_id    UUID;
BEGIN
    IF v_hash NOT LIKE '$2%' THEN
        RAISE WARNING
            'V52: super admin NOT created — password_hash placeholder was never replaced.';
        RETURN;
    END IF;

    v_email := lower(trim(v_email));

    -- Idempotent: an existing account keeps its password and is only granted the role.
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
        -- Make sure an existing account can actually sign in; PENDING_VERIFICATION or a
        -- disabled status would leave it locked out despite holding the role.
        UPDATE users
        SET status     = 'ACTIVE',
            updated_at = CURRENT_TIMESTAMP,
            updated_by = 'V52'
        WHERE id = v_id
          AND status <> 'ACTIVE';

        RAISE NOTICE 'V52: promoted existing account % (password unchanged)', v_email;
    END IF;

    INSERT INTO user_roles (user_id, role)
    VALUES (v_id, 'SUPER_ADMIN')
    ON CONFLICT DO NOTHING;
END $$;
