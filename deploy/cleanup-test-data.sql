-- Removes expo test data (test organizations, their leads, reservations, payments, media,
-- sponsorship applications and the users that only exist for them). Run on the droplet:
--
--   docker compose exec -T postgres psql -U housing_platform -d housing_platform \
--     -v ON_ERROR_STOP=1 -f - < deploy/cleanup-test-data.sql
--
-- Step 1 only reports. Nothing is deleted until you remove the ROLLBACK at the end and
-- replace it with COMMIT, after checking the report. Edit the two lists below first.
BEGIN;

-- 1. The organizations to remove, by exact name (case-insensitive) ...
CREATE TEMP TABLE cleanup_org_names (name text) ON COMMIT DROP;
INSERT INTO cleanup_org_names VALUES
  ('Rule check'),
  ('New test'),
  ('New test bank'),
  ('Interest email test'),
  ('Dream Team Trading PLC (test)'),
  ('Chapa test (cancel me)'),
  ('Exhibition: habtamuwgs@icloud.com');

-- ... plus reservations to remove by reference, whatever organization they sit on.
CREATE TEMP TABLE cleanup_refs (reference text) ON COMMIT DROP;
INSERT INTO cleanup_refs VALUES
  ('RSV-Q7TUAA'), ('RSV-RWK6VR'), ('RSV-VTBZVX'), ('RSV-QYC22A'), ('RSV-LQFES2');

CREATE TEMP TABLE cleanup_orgs ON COMMIT DROP AS
  SELECT o.id, o.name FROM organizations o
  WHERE lower(o.name) IN (SELECT lower(name) FROM cleanup_org_names);

CREATE TEMP TABLE cleanup_reservations ON COMMIT DROP AS
  SELECT r.id, r.reference, r.company FROM sponsorship_reservation r
  WHERE r.organization_id IN (SELECT id FROM cleanup_orgs)
     OR upper(r.reference) IN (SELECT upper(reference) FROM cleanup_refs);

-- Leads of the kept reservations are listed too, so you can see what the relink will recreate.
-- Users that belong only to these organizations and have never set a password.
CREATE TEMP TABLE cleanup_users ON COMMIT DROP AS
  SELECT u.id, u.email FROM users u
  WHERE u.organization_id IN (SELECT id FROM cleanup_orgs)
    AND COALESCE(u.password_set, false) = false
    AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role IN ('ADMIN','SUPER_ADMIN'));

-- ---------------------------------------------------------------- report
SELECT 'organization' AS kind, name AS what FROM cleanup_orgs
UNION ALL SELECT 'reservation', reference || ' · ' || company FROM cleanup_reservations
UNION ALL SELECT 'user (unset password)', email FROM cleanup_users
UNION ALL SELECT 'lead', email FROM exhibition_interest WHERE organization_id IN (SELECT id FROM cleanup_orgs)
UNION ALL SELECT 'sponsorship application', status FROM sponsorship_applications WHERE organization_id IN (SELECT id FROM cleanup_orgs)
UNION ALL SELECT 'media', COALESCE(file_name, image_url) FROM media_attachments WHERE organization_id IN (SELECT id FROM cleanup_orgs)
ORDER BY 1, 2;

-- ---------------------------------------------------------------- delete
DELETE FROM reservation_payment WHERE reservation_id IN (SELECT id FROM cleanup_reservations);
UPDATE booth SET status = 'AVAILABLE', reservation_id = NULL, hold_expires_at = NULL
  WHERE reservation_id IN (SELECT id FROM cleanup_reservations);
DELETE FROM sponsorship_reservation WHERE id IN (SELECT id FROM cleanup_reservations);
-- A kept reservation may still point at a lead of a test organization: detach it first.
-- (Re-run POST /api/v1/admin/sponsorship-reservations/link-organizations afterwards, or click
-- "link now" on the Reservations page, to give it a fresh lead.)
UPDATE sponsorship_reservation SET exhibition_interest_id = NULL
  WHERE exhibition_interest_id IN (SELECT id FROM exhibition_interest WHERE organization_id IN (SELECT id FROM cleanup_orgs));
UPDATE sponsorship_reservation SET organization_id = NULL
  WHERE organization_id IN (SELECT id FROM cleanup_orgs);
DELETE FROM exhibition_interest WHERE organization_id IN (SELECT id FROM cleanup_orgs);
DELETE FROM sponsorship_applications WHERE organization_id IN (SELECT id FROM cleanup_orgs);
DELETE FROM media_attachments WHERE organization_id IN (SELECT id FROM cleanup_orgs);
UPDATE organizations SET primary_contact_user_id = NULL WHERE id IN (SELECT id FROM cleanup_orgs);
DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM cleanup_users);
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM cleanup_users);
DELETE FROM users WHERE id IN (SELECT id FROM cleanup_users);
UPDATE users SET organization_id = NULL WHERE organization_id IN (SELECT id FROM cleanup_orgs);
DELETE FROM organization_phones WHERE contact_id IN (SELECT id FROM organization_contacts WHERE organization_id IN (SELECT id FROM cleanup_orgs));
DELETE FROM organization_contacts WHERE organization_id IN (SELECT id FROM cleanup_orgs);
DELETE FROM organization_supplier_subcategories WHERE organization_id IN (SELECT id FROM cleanup_orgs);
DELETE FROM organizations WHERE id IN (SELECT id FROM cleanup_orgs);

SELECT 'remaining reservations' AS kind, count(*)::text AS what FROM sponsorship_reservation
UNION ALL SELECT 'remaining organizations', count(*)::text FROM organizations;

-- Dry run by default. Change to COMMIT once the report above is what you expect.
ROLLBACK;
