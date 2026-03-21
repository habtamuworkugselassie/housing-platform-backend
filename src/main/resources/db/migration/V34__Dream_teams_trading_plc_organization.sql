-- Dream Teams Trading PLC: footer-aligned contact; approved org + active PREMIUM/EXCLUSIVE
-- sponsorship application so it appears on the public sponsored carousel.
-- Idempotent: safe if the org or contact rows already exist (e.g. created via API).

INSERT INTO organizations (
    id,
    name,
    registration_number,
    type,
    status,
    address,
    city,
    country,
    description,
    created_at,
    updated_at,
    version,
    created_by,
    updated_by
) VALUES (
    gen_random_uuid(),
    'Dream Teams Trading PLC',
    'DTT-PLC-FOOTER-001',
    'REAL_ESTATE_COMPANY',
    'APPROVED',
    'Addis Ababa, Ethiopia',
    'Addis Ababa',
    'Ethiopia',
    'Dream Teams Trading PLC — partner organization (contact aligned with site footer).',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'migration',
    'migration'
)
ON CONFLICT (registration_number) DO NOTHING;

INSERT INTO organization_contacts (
    id,
    organization_id,
    email,
    website,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    o.id,
    NULL,
    'https://www.dreamteam.com',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM organizations o
WHERE o.registration_number = 'DTT-PLC-FOOTER-001'
ON CONFLICT (organization_id) DO UPDATE SET
    website = EXCLUDED.website,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO organization_phones (
    id,
    contact_id,
    country_code,
    number,
    display_order,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    oc.id,
    '+251',
    '913504097',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM organization_contacts oc
JOIN organizations o ON o.id = oc.organization_id
WHERE o.registration_number = 'DTT-PLC-FOOTER-001'
  AND NOT EXISTS (
      SELECT 1
      FROM organization_phones op
      WHERE op.contact_id = oc.id
        AND op.country_code = '+251'
        AND op.number = '913504097'
  );

INSERT INTO sponsorship_applications (
    id,
    sponsorship_id,
    organization_id,
    status,
    start_date,
    end_date,
    notes,
    created_at,
    updated_at,
    version,
    created_by,
    updated_by
)
SELECT
    gen_random_uuid(),
    s.id,
    o.id,
    'APPROVED',
    date_trunc('second', CURRENT_TIMESTAMP),
    date_trunc('day', CURRENT_TIMESTAMP) + interval '1 year' + interval '23 hours 59 minutes 59 seconds',
    'Seed: Dream Teams Trading PLC — active sponsorship for sponsored carousel (V34 migration).',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'migration',
    'migration'
FROM organizations o
CROSS JOIN LATERAL (
    SELECT id
    FROM sponsorships
    WHERE status = 'ACTIVE'
      AND type IN ('PREMIUM', 'EXCLUSIVE')
    ORDER BY CASE type WHEN 'EXCLUSIVE' THEN 0 ELSE 1 END, base_price DESC
    LIMIT 1
) s
WHERE o.registration_number = 'DTT-PLC-FOOTER-001'
  AND NOT EXISTS (
      SELECT 1
      FROM sponsorship_applications sa
      WHERE sa.organization_id = o.id
        AND sa.sponsorship_id = s.id
        AND sa.status = 'APPROVED'
  );
