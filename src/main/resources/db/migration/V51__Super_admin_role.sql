-- Super admin tier.
--
-- user_roles.role is a plain VARCHAR(50) with no check constraint, so the new SUPER_ADMIN value
-- needs no schema change — only a bootstrap grant, because a role nobody holds cannot be handed
-- out through the UI it gates.
--
-- Bootstrap rule: the longest-standing ADMIN account becomes the first super admin. Promote anyone
-- else afterwards from Admin -> Users, or with:
--   INSERT INTO user_roles (user_id, role)
--   SELECT id, 'SUPER_ADMIN' FROM users WHERE email = 'someone@example.com'
--   ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT u.id, 'SUPER_ADMIN'
FROM users u
         JOIN user_roles r ON r.user_id = u.id AND r.role = 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE role = 'SUPER_ADMIN')
ORDER BY u.created_at ASC
LIMIT 1;

-- Company logins are looked up per organization on the admin Accounts panel.
CREATE INDEX IF NOT EXISTS idx_users_organization_id ON users (organization_id);
