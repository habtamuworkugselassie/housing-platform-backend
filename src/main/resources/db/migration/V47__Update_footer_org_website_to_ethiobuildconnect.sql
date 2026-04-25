-- Set the footer organization's public website to Ethio Build Connect.
-- This organization is used by DisplaySettingsService via FOOTER_ORGANIZATION_REGISTRATION_NUMBER.
UPDATE organization_contacts oc
SET website = 'https://ethiobuildconnect.et/',
    updated_at = CURRENT_TIMESTAMP
FROM organizations o
WHERE o.id = oc.organization_id
  AND o.registration_number = 'DTT-PLC-FOOTER-001';
