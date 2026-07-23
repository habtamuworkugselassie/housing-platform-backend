-- Partnership proposals can target the exhibition, the overall platform, or both,
-- and may be cash, in-kind, or hybrid arrangements.
ALTER TABLE exhibition_interest
    ADD COLUMN IF NOT EXISTS partner_role VARCHAR(32),
    ADD COLUMN IF NOT EXISTS visibility_scope VARCHAR(32),
    ADD COLUMN IF NOT EXISTS contribution_mode VARCHAR(32);

ALTER TABLE exhibition_interest
    ADD CONSTRAINT chk_exhibition_interest_partner_role
        CHECK (partner_role IS NULL OR partner_role IN ('SPONSOR', 'MEDIA_PARTNER')),
    ADD CONSTRAINT chk_exhibition_interest_visibility_scope
        CHECK (visibility_scope IS NULL OR visibility_scope IN ('EXHIBITION', 'PLATFORM', 'BOTH')),
    ADD CONSTRAINT chk_exhibition_interest_contribution_mode
        CHECK (contribution_mode IS NULL OR contribution_mode IN ('CASH', 'IN_KIND', 'HYBRID'));
