-- Add dual currency pricing support for properties
-- Properties can have prices in both ETB and USD, but transactions use one currency

-- Add new price columns
ALTER TABLE properties 
ADD COLUMN IF NOT EXISTS price_etb NUMERIC(19, 2),
ADD COLUMN IF NOT EXISTS price_usd NUMERIC(19, 2);

-- Migrate existing data: move price to price_etb (defaulting to ETB since we don't have historical currency)
UPDATE properties 
SET price_etb = price 
WHERE price_etb IS NULL AND price IS NOT NULL;

-- Make price nullable temporarily (we'll drop it after migration)
-- First check if NOT NULL constraint exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'properties_price_not_null' 
        AND table_name = 'properties'
    ) THEN
        ALTER TABLE properties ALTER COLUMN price DROP NOT NULL;
    END IF;
END $$;

-- Add constraint: at least one price must be provided
-- Drop constraint if it exists first
ALTER TABLE properties DROP CONSTRAINT IF EXISTS chk_properties_price;
ALTER TABLE properties 
ADD CONSTRAINT chk_properties_price CHECK (
    (price_etb IS NOT NULL AND price_etb > 0) OR 
    (price_usd IS NOT NULL AND price_usd > 0)
);

-- Drop the old currency column (no longer needed)
ALTER TABLE properties DROP COLUMN IF EXISTS currency;

-- Note: We keep the old 'price' column for now to allow gradual migration
-- It can be dropped in a future migration after all clients are updated
