-- Merge legacy consultant/architect organization types into a single type.
UPDATE organizations
SET type = 'CONSULTANT_ARCHITECT'
WHERE type IN ('CONSULTANT', 'ARCHITECT');
