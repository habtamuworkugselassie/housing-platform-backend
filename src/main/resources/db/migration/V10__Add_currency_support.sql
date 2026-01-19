-- Add currency support across the platform
-- Supports ETB (Ethiopian Birr) and USD (US Dollar)

-- Add currency to properties table
ALTER TABLE properties 
ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'ETB';

-- Add currency to credit_products table
ALTER TABLE credit_products 
ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'ETB';

-- Add currency to loan_applications table
ALTER TABLE loan_applications 
ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'ETB';

-- Add approved_currency to loan_applications table
ALTER TABLE loan_applications 
ADD COLUMN IF NOT EXISTS approved_currency VARCHAR(10);

-- Update payments table currency default to ETB (was USD)
ALTER TABLE payments 
ALTER COLUMN currency SET DEFAULT 'ETB';

-- Update existing records to have ETB as default currency
UPDATE properties SET currency = 'ETB' WHERE currency IS NULL;
UPDATE credit_products SET currency = 'ETB' WHERE currency IS NULL;
UPDATE loan_applications SET currency = 'ETB' WHERE currency IS NULL;
UPDATE payments SET currency = 'ETB' WHERE currency IS NULL OR currency = 'USD';

-- Add check constraints to ensure only valid currencies
ALTER TABLE properties 
ADD CONSTRAINT chk_properties_currency CHECK (currency IN ('ETB', 'USD'));

ALTER TABLE credit_products 
ADD CONSTRAINT chk_credit_products_currency CHECK (currency IN ('ETB', 'USD'));

ALTER TABLE loan_applications 
ADD CONSTRAINT chk_loan_applications_currency CHECK (currency IN ('ETB', 'USD'));

ALTER TABLE loan_applications 
ADD CONSTRAINT chk_loan_applications_approved_currency CHECK (approved_currency IS NULL OR approved_currency IN ('ETB', 'USD'));

ALTER TABLE payments 
ADD CONSTRAINT chk_payments_currency CHECK (currency IN ('ETB', 'USD'));

-- Create indexes for currency columns (useful for filtering)
CREATE INDEX IF NOT EXISTS idx_properties_currency ON properties(currency);
CREATE INDEX IF NOT EXISTS idx_credit_products_currency ON credit_products(currency);
CREATE INDEX IF NOT EXISTS idx_loan_applications_currency ON loan_applications(currency);
