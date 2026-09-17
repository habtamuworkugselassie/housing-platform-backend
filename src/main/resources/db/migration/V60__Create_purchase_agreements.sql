-- Agreements signed between the buyer and the platform provider on a purchase order.

CREATE TABLE agreement_templates (
    id                UUID PRIMARY KEY,
    type              VARCHAR(40)  NOT NULL,
    template_version  INTEGER      NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              TEXT         NOT NULL,
    issue_trigger     VARCHAR(32)  NOT NULL,
    applies_to        VARCHAR(16)  NOT NULL DEFAULT 'ALL',
    sequence          INTEGER      NOT NULL DEFAULT 0,
    blocks_completion BOOLEAN      NOT NULL DEFAULT TRUE,
    active            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    version           BIGINT,
    CONSTRAINT uq_agreement_templates_type_version UNIQUE (type, template_version)
);

CREATE TABLE purchase_agreements (
    id                          UUID PRIMARY KEY,
    purchase_order_id           UUID         NOT NULL REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    template_id                 UUID         NOT NULL REFERENCES agreement_templates(id),
    type                        VARCHAR(40)  NOT NULL,
    template_version            INTEGER      NOT NULL,
    sequence                    INTEGER      NOT NULL,
    title                       VARCHAR(255) NOT NULL,
    content                     TEXT         NOT NULL,
    content_hash                VARCHAR(64)  NOT NULL,
    status                      VARCHAR(32)  NOT NULL,
    blocks_completion           BOOLEAN      NOT NULL,
    issued_at                   TIMESTAMP    NOT NULL,
    buyer_user_id               UUID         NOT NULL REFERENCES users(id),
    buyer_signatory_name        VARCHAR(255),
    buyer_signature_method      VARCHAR(32),
    buyer_signed_at             TIMESTAMP,
    buyer_signature_ip          VARCHAR(64),
    buyer_signature_user_agent  VARCHAR(512),
    provider_name               VARCHAR(255) NOT NULL,
    provider_signatory_name     VARCHAR(255),
    provider_signatory_title    VARCHAR(255),
    provider_signed_at          TIMESTAMP,
    provider_signed_by_user_id  UUID,
    voided_at                   TIMESTAMP,
    void_reason                 TEXT,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP    NOT NULL,
    created_by                  VARCHAR(255),
    updated_by                  VARCHAR(255),
    version                     BIGINT
);

CREATE INDEX idx_purchase_agreements_order  ON purchase_agreements (purchase_order_id, sequence);
CREATE INDEX idx_purchase_agreements_status ON purchase_agreements (status);

-- Seed: version 1 of the Promise to Purchase, signed inside order creation.
-- Placeholders are filled by AgreementTemplateRenderer; {{#financing}}…{{/financing}} appears only
-- on bank-financed orders and {{^financing}}…{{/financing}} only on cash orders.
INSERT INTO agreement_templates
    (id, type, template_version, title, body, issue_trigger, applies_to, sequence, blocks_completion, active, created_at, updated_at, created_by)
VALUES (
    'a1000000-0000-4000-8000-000000000001',
    'PROMISE_TO_PURCHASE', 1,
    'Promise to Purchase Agreement',
$body$# PROMISE TO PURCHASE AGREEMENT

**Reference:** {{order.number}}
**Date:** {{date.today}}

This Promise to Purchase Agreement (the "Agreement") is made between:

1. **{{provider.name}}**{{#provider.registrationNumber}} (Registration No. {{provider.registrationNumber}}){{/provider.registrationNumber}}, of {{provider.address}} (the "Provider"), operator of the Ethio Build Connect platform; and
2. **{{buyer.fullName}}**, reachable at {{buyer.phone}}{{#buyer.email}} and {{buyer.email}}{{/buyer.email}} (the "Buyer").

## 1. The Property

The Buyer promises to purchase, through the Provider's platform, the property listed as **{{property.title}}**{{#property.unitNumber}}, unit {{property.unitNumber}}{{/property.unitNumber}}, located at {{property.address}}, {{property.city}} (the "Property"), offered by {{seller.companyName}} (the "Seller").

## 2. Price

The listed purchase price of the Property is **{{price.amount}} {{price.currency}}**. The final price and payment schedule will be fixed in the Sale Agreement to be concluded between the Buyer and the Seller.

{{#financing}}
## 3. Financing

The Buyer intends to finance **{{financing.financedAmount}} {{price.currency}}** of the price through **{{financing.bankName}}** and to contribute **{{financing.cashPortion}} {{price.currency}}** from own funds. The Buyer acknowledges that the loan application has been submitted to the bank and that the bank's decision is outside the Provider's control. If financing is declined the Buyer may re-apply for a smaller amount, convert this order to a cash purchase, or cancel it.
{{/financing}}
{{^financing}}
## 3. Payment

The Buyer intends to pay the purchase price from own funds without bank financing arranged through the platform.
{{/financing}}

## 4. Nature of this Promise

4.1 This Agreement records the Buyer's serious intention to purchase the Property and authorises the Provider to forward the Buyer's order and contact details to the Seller{{#financing}} and to the financing bank{{/financing}}.

4.2 This Agreement does not transfer ownership of the Property. Transfer takes place only under the Sale Agreement and the applicable laws of the Federal Democratic Republic of Ethiopia.

4.3 The Seller may accept or decline the order. If the Seller does not respond within fourteen (14) days the order expires and this Agreement lapses without liability.

## 5. Obligations of the Buyer

The Buyer shall provide accurate information, respond to requests from the Seller{{#financing}} and the bank{{/financing}} in good time, and inform the Provider promptly of any decision to withdraw.

## 6. Obligations of the Provider

The Provider shall transmit the order to the Seller, keep the Buyer informed of its status through the platform and the contact details above, and keep the Buyer's data in accordance with its privacy policy.

## 7. Withdrawal

The Buyer may cancel the order at any time before the sale is completed. Any deposit or fee paid to the Seller is governed by the Sale Agreement, not by this Agreement.

## 8. Governing Law

This Agreement is governed by the laws of the Federal Democratic Republic of Ethiopia.

## 9. Electronic Signature

The parties agree that this Agreement is concluded electronically. The Buyer signs by entering their full name and confirming acceptance while authenticated on the platform; the Provider signs through its authorised signatory. The signed text is identified by its SHA-256 fingerprint recorded with the signatures.

---

**For the Provider:** {{provider.signatoryName}}, {{provider.signatoryTitle}}, {{provider.name}}

**The Buyer:** {{buyer.fullName}}
$body$,
    'ORDER_CREATION', 'ALL', 1, TRUE, TRUE, NOW(), NOW(), 'system'
);

-- Seed: draft follow-up agreements, issued automatically once activated by an admin after legal review.
INSERT INTO agreement_templates
    (id, type, template_version, title, body, issue_trigger, applies_to, sequence, blocks_completion, active, created_at, updated_at, created_by)
VALUES (
    'a1000000-0000-4000-8000-000000000002',
    'SALE_AGREEMENT', 1,
    'Sale Agreement Acknowledgement',
$body$# SALE AGREEMENT ACKNOWLEDGEMENT

**Reference:** {{order.number}} · **Date:** {{date.today}}

Between **{{provider.name}}** (the "Provider") and **{{buyer.fullName}}** (the "Buyer").

The Seller, {{seller.companyName}}, has accepted the Buyer's order for **{{property.title}}** at the listed price of **{{price.amount}} {{price.currency}}**.

The Buyer acknowledges that the sale contract for the Property is concluded directly with the Seller and undertakes to sign it, and to make the payments it provides for, before the Provider records the sale as completed. The Provider undertakes to record the completion only after the Seller confirms payment.

**For the Provider:** {{provider.signatoryName}}, {{provider.signatoryTitle}} · **The Buyer:** {{buyer.fullName}}
$body$,
    'SELLER_ACCEPTANCE', 'ALL', 2, TRUE, FALSE, NOW(), NOW(), 'system'
), (
    'a1000000-0000-4000-8000-000000000003',
    'FINANCING_ACKNOWLEDGEMENT', 1,
    'Financing Acknowledgement',
$body$# FINANCING ACKNOWLEDGEMENT

**Reference:** {{order.number}} · **Date:** {{date.today}}

Between **{{provider.name}}** (the "Provider") and **{{buyer.fullName}}** (the "Buyer").

{{financing.bankName}} has approved financing of **{{financing.approvedAmount}} {{price.currency}}** towards the purchase of **{{property.title}}**. The Buyer acknowledges that the remaining **{{financing.cashPortion}} {{price.currency}}** is payable from own funds and that the loan is governed by the Buyer's separate agreement with the bank.

**For the Provider:** {{provider.signatoryName}}, {{provider.signatoryTitle}} · **The Buyer:** {{buyer.fullName}}
$body$,
    'FINANCING_APPROVAL', 'FINANCED_ONLY', 3, TRUE, FALSE, NOW(), NOW(), 'system'
);
