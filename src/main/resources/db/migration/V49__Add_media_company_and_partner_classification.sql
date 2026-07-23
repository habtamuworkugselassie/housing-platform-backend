-- Classify sponsorship packages independently from their commercial tier.
-- Existing packages remain cash sponsors visible on both the platform and exhibition.
ALTER TABLE sponsorships
    ADD COLUMN IF NOT EXISTS partner_role VARCHAR(32) NOT NULL DEFAULT 'SPONSOR',
    ADD COLUMN IF NOT EXISTS visibility_scope VARCHAR(32) NOT NULL DEFAULT 'BOTH',
    ADD COLUMN IF NOT EXISTS contribution_mode VARCHAR(32) NOT NULL DEFAULT 'CASH';

ALTER TABLE sponsorships
    ADD CONSTRAINT chk_sponsorship_partner_role
        CHECK (partner_role IN ('SPONSOR', 'MEDIA_PARTNER')),
    ADD CONSTRAINT chk_sponsorship_visibility_scope
        CHECK (visibility_scope IN ('EXHIBITION', 'PLATFORM', 'BOTH')),
    ADD CONSTRAINT chk_sponsorship_contribution_mode
        CHECK (contribution_mode IN ('CASH', 'IN_KIND', 'HYBRID'));
