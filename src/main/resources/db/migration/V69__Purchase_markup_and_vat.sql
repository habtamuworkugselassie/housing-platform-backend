-- The provider's markup and VAT, paid by the buyer on top of the price as a service fee once the
-- seller accepts. Fixed on each order when it is placed (rates are admin settings).
ALTER TABLE property_purchase_orders ADD COLUMN fee_markup_percent NUMERIC(7,4);
ALTER TABLE property_purchase_orders ADD COLUMN fee_markup_amount  NUMERIC(19,2);
ALTER TABLE property_purchase_orders ADD COLUMN fee_vat_rate       NUMERIC(7,4);
ALTER TABLE property_purchase_orders ADD COLUMN fee_vat_amount     NUMERIC(19,2);

ALTER TABLE purchase_balance_payments ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'BALANCE';
ALTER TABLE purchase_balance_payments ADD CONSTRAINT chk_balance_payment_purpose CHECK (purpose IN ('BALANCE','FEES'));

-- VAT defaults to the standard 15 %; the markup stays off (0) until an admin sets it.
INSERT INTO site_settings (setting_key, setting_value, updated_at)
VALUES ('purchase.fees.vatRate', '15', NOW())
ON CONFLICT (setting_key) DO NOTHING;

-- Promise to Purchase v3: the active text plus the service fee clause, shown only when a fee
-- applies. Inserted before Annex A when present. Copied from the active row.
INSERT INTO agreement_templates
    (id, type, template_version, title, body, issue_trigger, applies_to, sequence, blocks_completion, active, created_at, updated_at, created_by)
SELECT
    'a1000000-0000-4000-8000-000000000008',
    t.type,
    t.template_version + 1,
    t.title,
    CASE WHEN position(E'\n\n---\n\n## Annex A' in t.body) > 0
         THEN replace(t.body, E'\n\n---\n\n## Annex A', E'\n\n{{#fees}}## Service fee and VAT\n\nIn addition to the price, the Buyer pays the Provider a service fee of **{{fees.markupPercent}} %** of the price (**{{fees.markupAmount}} {{price.currency}}**) and value added tax at **{{fees.vatRate}} %** on the price plus the service fee (**{{fees.vatAmount}} {{price.currency}}**), in total **{{fees.total}} {{price.currency}}**. It is due once the Seller accepts the order, is paid through the platform, and is refunded in full if the sale does not proceed for a reason in the Reservation Deposit Terms that entitles the Buyer to a full refund.\n\n{{/fees}}---\n\n## Annex A')
         ELSE t.body || E'\n\n{{#fees}}## Service fee and VAT\n\nIn addition to the price, the Buyer pays the Provider a service fee of **{{fees.markupPercent}} %** of the price (**{{fees.markupAmount}} {{price.currency}}**) and value added tax at **{{fees.vatRate}} %** on the price plus the service fee (**{{fees.vatAmount}} {{price.currency}}**), in total **{{fees.total}} {{price.currency}}**. It is due once the Seller accepts the order and is paid through the platform.\n{{/fees}}'
    END,
    t.issue_trigger, t.applies_to, t.sequence, t.blocks_completion, TRUE, NOW(), NOW(), 'system'
FROM agreement_templates t
WHERE t.type = 'PROMISE_TO_PURCHASE' AND t.active = TRUE
ORDER BY t.template_version DESC
LIMIT 1;

UPDATE agreement_templates SET active = FALSE, updated_at = NOW()
 WHERE type = 'PROMISE_TO_PURCHASE' AND active = TRUE
   AND id <> 'a1000000-0000-4000-8000-000000000008';
