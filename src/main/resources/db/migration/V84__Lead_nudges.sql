-- Automatic "reserve your place" nudges to leads that picked a package but never reserved.
ALTER TABLE exhibition_interest ADD COLUMN IF NOT EXISTS nudge_count INTEGER NOT NULL DEFAULT 0;
