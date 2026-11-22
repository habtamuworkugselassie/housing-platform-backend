-- Exhibitor self-service: a paid reservation becomes an organization account,
-- and media uploaded by exhibitors waits for admin review before going public.
ALTER TABLE sponsorship_reservation ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id);
ALTER TABLE sponsorship_reservation ADD COLUMN IF NOT EXISTS exhibitor_invited_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_sponsorship_reservation_org ON sponsorship_reservation(organization_id);

ALTER TABLE media_attachments ADD COLUMN IF NOT EXISTS review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE media_attachments ADD COLUMN IF NOT EXISTS review_note TEXT;
CREATE INDEX IF NOT EXISTS idx_media_attachments_review ON media_attachments(review_status);
