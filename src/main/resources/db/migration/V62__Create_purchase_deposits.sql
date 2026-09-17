-- Reservation deposit paid by the buyer to the provider through Chapa's hosted checkout.

CREATE TABLE purchase_deposits (
    id                  UUID PRIMARY KEY,
    purchase_order_id   UUID          NOT NULL UNIQUE REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    amount              NUMERIC(19,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    status              VARCHAR(24)   NOT NULL,
    due_at              TIMESTAMP,
    tx_ref              VARCHAR(64)   UNIQUE,
    checkout_url        VARCHAR(1024),
    provider            VARCHAR(32)   NOT NULL DEFAULT 'CHAPA',
    provider_reference  VARCHAR(255),
    payment_method      VARCHAR(64),
    paid_at             TIMESTAMP,
    failure_reason      TEXT,
    attempts            INTEGER       NOT NULL DEFAULT 0,
    waived_by_user_id   UUID,
    waive_reason        TEXT,
    refund_reference    VARCHAR(255),
    refunded_at         TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    version             BIGINT,
    CONSTRAINT chk_purchase_deposits_status CHECK (status IN ('DUE','PENDING','PAID','FAILED','CANCELLED','WAIVED','REFUND_PENDING','REFUNDED'))
);

CREATE INDEX idx_purchase_deposits_status ON purchase_deposits (status);

-- Seed: reservation deposit terms, issued on seller acceptance and signed before the deposit is paid.
-- Draft for legal review, active because the deposit flow requires it.
INSERT INTO agreement_templates
    (id, type, template_version, title, body, issue_trigger, applies_to, sequence, blocks_completion, active, created_at, updated_at, created_by)
VALUES (
    'a1000000-0000-4000-8000-000000000004',
    'RESERVATION_DEPOSIT_TERMS', 1,
    'Reservation Deposit Terms',
$body$# RESERVATION DEPOSIT TERMS

**Reference:** {{order.number}} · **Date:** {{date.today}}

Between **{{provider.name}}** (the "Provider") and **{{buyer.fullName}}** (the "Buyer").

## 1. Purpose

The Seller, {{seller.companyName}}, has accepted the Buyer's order for **{{property.title}}** at the listed price of **{{price.amount}} {{price.currency}}**. To reserve the Property while the sale is arranged, the Buyer pays the Provider a reservation deposit.

## 2. Amount and payment

2.1 The reservation deposit is **{{deposit.amount}} {{deposit.currency}}**, payable by {{deposit.dueDate}} through the payment provider offered on the platform. The Buyer's card or account details are entered with the payment provider and are never stored by the Provider.

2.2 The Provider holds the deposit on the Buyer's behalf as a reservation fee for the Property.

## 3. Application of the deposit

3.1 When the sale completes, the Provider applies the deposit to the amounts due under the Sale Agreement, or refunds it to the Buyer if the Seller and the Buyer settle the full price directly, as the parties agree in writing.

3.2 If the Seller withdraws, the Property becomes unavailable, or financing is declined and the Buyer does not continue as a cash purchase, the deposit is refunded in full within fourteen (14) days.

3.3 If the Buyer withdraws after paying the deposit for reasons other than those in clause 3.2, the Provider may retain a handling fee of up to ten percent (10%) of the deposit to cover costs incurred, and refunds the balance.

## 4. Refund method

Refunds are made to the payment method the deposit was paid from, less any charges the payment provider does not return.

## 5. Governing law

These terms are governed by the laws of the Federal Democratic Republic of Ethiopia.

## 6. Electronic signature

The Buyer signs by entering their full name and confirming acceptance while authenticated on the platform; the Provider signs through its authorised signatory. The signed text is identified by its SHA-256 fingerprint recorded with the signatures.

---

**For the Provider:** {{provider.signatoryName}}, {{provider.signatoryTitle}}, {{provider.name}}

**The Buyer:** {{buyer.fullName}}
$body$,
    'SELLER_ACCEPTANCE', 'ALL', 2, TRUE, TRUE, NOW(), NOW(), 'system'
);
