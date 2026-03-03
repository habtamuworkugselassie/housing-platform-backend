-- Rename sponsorship tiers:
--   PREMIER -> PREMIUM
--   BASIC   -> GOLD

UPDATE sponsorships
SET type = 'PREMIUM'
WHERE type = 'PREMIER';

UPDATE sponsorships
SET type = 'GOLD'
WHERE type = 'BASIC';

-- Legacy safety: if an older schema still contains organizations.sponsorship_type,
-- migrate those values as well.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'organizations'
          AND column_name = 'sponsorship_type'
    ) THEN
        EXECUTE 'UPDATE organizations SET sponsorship_type = ''PREMIUM'' WHERE sponsorship_type = ''PREMIER''';
        EXECUTE 'UPDATE organizations SET sponsorship_type = ''GOLD'' WHERE sponsorship_type = ''BASIC''';
    END IF;
END $$;
