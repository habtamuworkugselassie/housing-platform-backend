-- A second published contact line for the footer organization (Dream Teams Trading PLC).
-- display_order 1 puts it after the existing +251 913 504 097, which stays primary.
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
    '920783807',
    1,
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
        AND op.number = '920783807'
  );
