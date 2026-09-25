-- Paying the balance of a purchase order (price - reservation deposit - bank loan) to the provider:
-- a schedule of instalments set by the seller, and the payments made against it online (Chapa) or
-- by bank transfer (slip confirmed by the seller or an admin). Payments count against the schedule
-- in order; completion is blocked until the balance is covered.

CREATE TABLE purchase_balance_instalments (
    id                UUID PRIMARY KEY,
    purchase_order_id UUID          NOT NULL REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    sequence          INTEGER       NOT NULL,
    label             VARCHAR(100)  NOT NULL,
    amount            NUMERIC(19,2) NOT NULL,
    due_date          DATE,
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    version           BIGINT,
    CONSTRAINT chk_balance_instalment_amount CHECK (amount > 0)
);
CREATE INDEX idx_balance_instalments_order ON purchase_balance_instalments (purchase_order_id, sequence);

CREATE TABLE purchase_balance_payments (
    id                  UUID PRIMARY KEY,
    purchase_order_id   UUID          NOT NULL REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    channel             VARCHAR(24)   NOT NULL,
    status              VARCHAR(24)   NOT NULL,
    amount              NUMERIC(19,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    tx_ref              VARCHAR(64)   UNIQUE,
    checkout_url        VARCHAR(1024),
    preferred_method    VARCHAR(24),
    provider_reference  VARCHAR(255),
    payment_method      VARCHAR(64),
    reference           VARCHAR(255),
    paid_on             DATE,
    slip_url            TEXT,
    slip_file_name      VARCHAR(255),
    slip_content_type   VARCHAR(100),
    submitted_by_user_id UUID,
    reviewed_by_user_id UUID,
    reviewed_at         TIMESTAMP,
    note                TEXT,
    paid_at             TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    version             BIGINT,
    CONSTRAINT chk_balance_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_balance_payment_channel CHECK (channel IN ('ONLINE','BANK_TRANSFER','RECORDED')),
    CONSTRAINT chk_balance_payment_status CHECK (status IN ('PENDING','SUBMITTED','PAID','FAILED','REJECTED','CANCELLED','REFUND_PENDING','REFUNDED'))
);
CREATE INDEX idx_balance_payments_order ON purchase_balance_payments (purchase_order_id);
CREATE INDEX idx_balance_payments_status ON purchase_balance_payments (status);
