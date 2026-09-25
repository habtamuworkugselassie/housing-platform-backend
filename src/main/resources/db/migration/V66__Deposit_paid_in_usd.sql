-- Buyers may pay an ETB reservation deposit in USD by international card. The deposit row keeps
-- the USD amount charged; these columns record the ETB amount it stands for and the rate used.
ALTER TABLE purchase_deposits ADD COLUMN base_amount   NUMERIC(19,2);
ALTER TABLE purchase_deposits ADD COLUMN base_currency VARCHAR(3);
ALTER TABLE purchase_deposits ADD COLUMN exchange_rate NUMERIC(19,6);

-- Deposit terms v3 = the current active terms plus a clause shown only for a deposit converted to
-- another currency. Copied from the active row so admin edits to v2 carry over.
INSERT INTO agreement_templates
    (id, type, template_version, title, body, issue_trigger, applies_to, sequence, blocks_completion, active, created_at, updated_at, created_by)
SELECT
    'a1000000-0000-4000-8000-000000000006',
    t.type,
    t.template_version + 1,
    t.title,
    replace(
        t.body,
        E'2.2 The Provider holds',
        E'{{#deposit.converted}}2.1a The Buyer has chosen to pay the deposit in {{deposit.currency}} by international card. The amount above is the {{deposit.baseAmount}} {{deposit.baseCurrency}} deposit converted at {{deposit.rate}} {{deposit.baseCurrency}} per {{deposit.currency}}. Refunds are made in {{deposit.currency}} for the amount paid.\n\n{{/deposit.converted}}2.2 The Provider holds'),
    t.issue_trigger, t.applies_to, t.sequence, t.blocks_completion, TRUE, NOW(), NOW(), 'system'
FROM agreement_templates t
WHERE t.type = 'RESERVATION_DEPOSIT_TERMS' AND t.active = TRUE
ORDER BY t.template_version DESC
LIMIT 1;

UPDATE agreement_templates SET active = FALSE, updated_at = NOW()
 WHERE type = 'RESERVATION_DEPOSIT_TERMS' AND active = TRUE
   AND id <> 'a1000000-0000-4000-8000-000000000006';
