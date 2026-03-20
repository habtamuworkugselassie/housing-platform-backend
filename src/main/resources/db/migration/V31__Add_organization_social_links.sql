-- Optional social / profile URLs for organization contact (marketplace & dashboards).
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS facebook_url VARCHAR(2048);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS instagram_url VARCHAR(2048);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS linkedin_url VARCHAR(2048);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS twitter_url VARCHAR(2048);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS youtube_url VARCHAR(2048);
