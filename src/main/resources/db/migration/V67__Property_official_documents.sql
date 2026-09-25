-- Official property documents (title deed / ownership certificate, land lease contract, permits).
-- Sellers upload them, admins verify them, and verified ones are listed in the Promise to Purchase.
-- Files live under the uploads volume in private/, which the public uploads route refuses.
ALTER TABLE property_documents ADD COLUMN document_number     VARCHAR(100);
ALTER TABLE property_documents ADD COLUMN issuing_authority   VARCHAR(255);
ALTER TABLE property_documents ADD COLUMN issued_on           DATE;
ALTER TABLE property_documents ADD COLUMN content_type        VARCHAR(100);
ALTER TABLE property_documents ADD COLUMN size_bytes          BIGINT;
ALTER TABLE property_documents ADD COLUMN sha256              VARCHAR(64);
ALTER TABLE property_documents ADD COLUMN uploaded_by_user_id UUID;
ALTER TABLE property_documents ADD COLUMN reviewed_by_user_id UUID;
ALTER TABLE property_documents ADD COLUMN reviewed_at         TIMESTAMP;
ALTER TABLE property_documents ADD COLUMN review_note         TEXT;

CREATE INDEX IF NOT EXISTS idx_property_documents_property ON property_documents (property_id);
CREATE INDEX IF NOT EXISTS idx_property_documents_status   ON property_documents (status);

-- Promise to Purchase: new version = the active text plus Annex A listing the verified documents.
-- Copied from the active row so admin edits carry over.
INSERT INTO agreement_templates
    (id, type, template_version, title, body, issue_trigger, applies_to, sequence, blocks_completion, active, created_at, updated_at, created_by)
SELECT
    'a1000000-0000-4000-8000-000000000007',
    t.type,
    t.template_version + 1,
    t.title,
    t.body || E'\n\n---\n\n## Annex A — Property documents\n\n{{#documents}}The Seller has provided the following official documents for the Property. The Provider has checked them against the originals presented. Each file is identified by its SHA-256 fingerprint, recorded when this agreement was issued; the Buyer may view the files on the platform.\n\n{{documents.annex}}{{/documents}}{{^documents}}No verified official documents (title deed, lease contract) were on file for the Property when this agreement was issued. The Buyer may ask the Provider for them before completion.{{/documents}}\n',
    t.issue_trigger, t.applies_to, t.sequence, t.blocks_completion, TRUE, NOW(), NOW(), 'system'
FROM agreement_templates t
WHERE t.type = 'PROMISE_TO_PURCHASE' AND t.active = TRUE
ORDER BY t.template_version DESC
LIMIT 1;

UPDATE agreement_templates SET active = FALSE, updated_at = NOW()
 WHERE type = 'PROMISE_TO_PURCHASE' AND active = TRUE
   AND id <> 'a1000000-0000-4000-8000-000000000007';
