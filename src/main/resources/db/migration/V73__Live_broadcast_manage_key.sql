-- SHA-256 (hex) of the secret handed to whoever requested the stream. Visitor broadcasts have no
-- account, so this key is what proves "I am the broadcaster" for publish / end / moderation.
-- NULL for broadcasts created before this column existed.
ALTER TABLE live_broadcast ADD COLUMN IF NOT EXISTS manage_key_hash VARCHAR(64);
