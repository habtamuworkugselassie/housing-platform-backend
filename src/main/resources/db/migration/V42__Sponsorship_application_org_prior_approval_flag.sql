-- True when the org was already APPROVED before this application (marketplace apply / admin assign).
-- False for applications created from exhibition landing (org was PENDING_APPROVAL) so reject/cancel restores PENDING_APPROVAL.
ALTER TABLE sponsorship_applications
    ADD COLUMN IF NOT EXISTS organization_was_approved_before_application BOOLEAN NOT NULL DEFAULT TRUE;
