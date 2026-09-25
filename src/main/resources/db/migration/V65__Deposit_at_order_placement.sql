-- Pay the reservation deposit when placing the order instead of after the seller accepts.

-- The method the buyer picked on our side (telebirr, CBE Birr, ...), used to steer Chapa's checkout.
ALTER TABLE purchase_deposits ADD COLUMN preferred_method VARCHAR(24);

-- Deposit terms v2: issued (and normally signed) with the order. v1 stays for orders that have it.
UPDATE agreement_templates SET active = FALSE, updated_at = NOW()
 WHERE type = 'RESERVATION_DEPOSIT_TERMS' AND active = TRUE;

INSERT INTO agreement_templates
    (id, type, template_version, title, body, issue_trigger, applies_to, sequence, blocks_completion, active, created_at, updated_at, created_by)
VALUES (
    'a1000000-0000-4000-8000-000000000005',
    'RESERVATION_DEPOSIT_TERMS', 2,
    'Reservation Deposit Terms',
$body$# RESERVATION DEPOSIT TERMS

**Reference:** {{order.number}} · **Date:** {{date.today}}

Between **{{provider.name}}** (the "Provider") and **{{buyer.fullName}}** (the "Buyer").

## 1. Purpose

The Buyer is placing an order with the Seller, {{seller.companyName}}, for **{{property.title}}** at the listed price of **{{price.amount}} {{price.currency}}**. To show commitment and reserve the Property while the Seller reviews the order and the sale is arranged, the Buyer pays the Provider a reservation deposit when placing the order.

## 2. Amount and payment

2.1 The reservation deposit is **{{deposit.amount}} {{deposit.currency}}**, payable by {{deposit.dueDate}} through the payment provider offered on the platform. The Buyer's card or account details are entered with the payment provider and are never stored by the Provider.

2.2 The Provider holds the deposit on the Buyer's behalf as a reservation fee for the Property.

## 3. Application of the deposit

3.1 When the sale completes, the Provider applies the deposit to the amounts due under the Sale Agreement, or refunds it to the Buyer if the Seller and the Buyer settle the full price directly, as the parties agree in writing.

3.2 If the Seller declines the order or does not respond within the review period, the Seller withdraws, the Property becomes unavailable, or financing is declined and the Buyer does not continue as a cash purchase, the deposit is refunded in full within fourteen (14) days.

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
    'ORDER_CREATION', 'ALL', 2, TRUE, TRUE, NOW(), NOW(), 'system'
);
