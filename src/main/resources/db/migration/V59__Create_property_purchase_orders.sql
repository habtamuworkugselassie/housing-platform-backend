-- Property purchase orders with optional (and optionally partial) bank financing.

CREATE TABLE property_purchase_orders (
    id                      UUID PRIMARY KEY,
    order_number            VARCHAR(32)   NOT NULL UNIQUE,
    property_id             UUID          NOT NULL REFERENCES properties(id),
    buyer_id                UUID          NOT NULL REFERENCES users(id),
    real_estate_company_id  UUID,
    agent_id                UUID,
    contact_phone           VARCHAR(20)   NOT NULL,
    contact_email           VARCHAR(255),
    purchase_type           VARCHAR(20)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    listed_price            NUMERIC(19,2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    buyer_message           TEXT,
    expires_at              TIMESTAMP,
    cancellation_reason     TEXT,
    rejection_reason        TEXT,
    payment_reference       VARCHAR(255),
    created_at              TIMESTAMP     NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    version                 BIGINT,
    CONSTRAINT chk_ppo_purchase_type CHECK (purchase_type IN ('CASH', 'BANK_FINANCED'))
);

-- One open order per buyer per property: the race-safe twin of the service's 409 check.
CREATE UNIQUE INDEX uq_ppo_open_buyer_property
    ON property_purchase_orders (buyer_id, property_id)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED');

CREATE INDEX idx_ppo_buyer_created   ON property_purchase_orders (buyer_id, created_at DESC);
CREATE INDEX idx_ppo_property_status ON property_purchase_orders (property_id, status);
CREATE INDEX idx_ppo_company_created ON property_purchase_orders (real_estate_company_id, created_at DESC);
CREATE INDEX idx_ppo_pending_expiry  ON property_purchase_orders (expires_at)
    WHERE status = 'PENDING_SELLER_REVIEW';

CREATE TABLE purchase_order_financing (
    id                            UUID PRIMARY KEY,
    purchase_order_id             UUID          NOT NULL UNIQUE REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    financing_offer_id            UUID          NOT NULL REFERENCES financing_offers(id),
    bank_id                       UUID          NOT NULL,
    credit_product_id             UUID          NOT NULL REFERENCES credit_products(id),
    offer_level                   VARCHAR(16)   NOT NULL,
    applied_interest_rate         NUMERIC(5,2)  NOT NULL,
    applied_ltv_ratio             NUMERIC(5,2)  NOT NULL,
    min_financeable_amount        NUMERIC(19,2) NOT NULL,
    max_financeable_amount        NUMERIC(19,2) NOT NULL,
    financing_mode                VARCHAR(16)   NOT NULL,
    financed_amount               NUMERIC(19,2) NOT NULL,
    cash_portion_amount           NUMERIC(19,2) NOT NULL,
    financing_coverage_ratio      NUMERIC(5,4)  NOT NULL,
    tenure_months                 INTEGER       NOT NULL,
    estimated_monthly_installment NUMERIC(19,2),
    loan_application_id           UUID          REFERENCES loan_applications(id),
    financing_status              VARCHAR(32)   NOT NULL,
    approved_amount               NUMERIC(19,2),
    approved_interest_rate        NUMERIC(5,2),
    approved_tenure_months        INTEGER,
    created_at                    TIMESTAMP     NOT NULL,
    updated_at                    TIMESTAMP     NOT NULL,
    version                       BIGINT,
    CONSTRAINT chk_pof_mode  CHECK (financing_mode IN ('MAXIMUM', 'PARTIAL')),
    CONSTRAINT chk_pof_split CHECK (financed_amount > 0 AND cash_portion_amount >= 0)
);

CREATE INDEX idx_pof_bank_status ON purchase_order_financing (bank_id, financing_status);
CREATE INDEX idx_pof_loan        ON purchase_order_financing (loan_application_id);

CREATE TABLE purchase_order_status_history (
    id                UUID PRIMARY KEY,
    purchase_order_id UUID        NOT NULL REFERENCES property_purchase_orders(id) ON DELETE CASCADE,
    from_status       VARCHAR(32),
    to_status         VARCHAR(32) NOT NULL,
    changed_by        VARCHAR(255),
    changed_at        TIMESTAMP   NOT NULL,
    notes             TEXT,
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP   NOT NULL,
    version           BIGINT
);

CREATE INDEX idx_posh_order_changed ON purchase_order_status_history (purchase_order_id, changed_at);
