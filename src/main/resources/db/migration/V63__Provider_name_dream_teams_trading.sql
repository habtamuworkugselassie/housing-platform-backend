-- The platform provider is Dream Teams Trading PLC. The first agreements were issued while the
-- default provider name read "Dream Team PLC"; fix the display name on those rows.
--
-- `content` and `content_hash` are deliberately left alone: the signed text is the legal record
-- and its SHA-256 fingerprint must keep verifying. New agreements render the correct name from
-- `purchase.provider.name`.
UPDATE purchase_agreements
SET provider_name = 'Dream Teams Trading PLC'
WHERE provider_name = 'Dream Team PLC';
