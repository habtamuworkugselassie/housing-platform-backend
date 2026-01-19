-- Migration Script: Migrate Property Prices to Dual Currency Format
-- This script migrates existing property prices from the old 'price' column
-- to the new dual currency format (price_etb and price_usd)
--
-- Usage:
--   psql -U your_user -d your_database -f migrate-property-prices.sql
--   Or use the wrapper scripts: migrate-property-prices.sh or migrate-property-prices.ps1
--
-- This script is idempotent and safe to run multiple times.

DO $$
DECLARE
    migrated_count INTEGER := 0;
    total_properties INTEGER := 0;
    properties_with_price INTEGER := 0;
    properties_with_currency INTEGER := 0;
    exchange_rate NUMERIC(10, 4) := 55.0; -- Default ETB to USD exchange rate (adjust as needed)
BEGIN
    RAISE NOTICE 'Starting property price migration...';
    RAISE NOTICE '========================================';
    
    -- Check if price_etb and price_usd columns exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'properties' AND column_name = 'price_etb'
    ) THEN
        RAISE NOTICE 'Creating price_etb column...';
        ALTER TABLE properties ADD COLUMN price_etb NUMERIC(19, 2);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'properties' AND column_name = 'price_usd'
    ) THEN
        RAISE NOTICE 'Creating price_usd column...';
        ALTER TABLE properties ADD COLUMN price_usd NUMERIC(19, 2);
    END IF;
    
    -- Count total properties
    SELECT COUNT(*) INTO total_properties FROM properties;
    RAISE NOTICE 'Total properties in database: %', total_properties;
    
    -- Count properties with old price column
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'properties' AND column_name = 'price'
    ) THEN
        SELECT COUNT(*) INTO properties_with_price 
        FROM properties 
        WHERE price IS NOT NULL AND price > 0;
        RAISE NOTICE 'Properties with old price column: %', properties_with_price;
    ELSE
        RAISE NOTICE 'Old price column does not exist. Migration may have already been completed.';
    END IF;
    
    -- Check if currency column exists (it might have been dropped in V11)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'properties' AND column_name = 'currency'
    ) THEN
        SELECT COUNT(*) INTO properties_with_currency 
        FROM properties 
        WHERE currency IS NOT NULL;
        RAISE NOTICE 'Properties with currency column: %', properties_with_currency;
        
        -- Migrate based on currency field
        RAISE NOTICE 'Migrating prices based on currency field...';
        
        -- Migrate ETB prices
        UPDATE properties 
        SET price_etb = price 
        WHERE price IS NOT NULL 
          AND price > 0 
          AND currency = 'ETB' 
          AND (price_etb IS NULL OR price_etb = 0);
        
        GET DIAGNOSTICS migrated_count = ROW_COUNT;
        RAISE NOTICE 'Migrated % ETB prices to price_etb', migrated_count;
        
        -- Migrate USD prices
        UPDATE properties 
        SET price_usd = price 
        WHERE price IS NOT NULL 
          AND price > 0 
          AND currency = 'USD' 
          AND (price_usd IS NULL OR price_usd = 0);
        
        GET DIAGNOSTICS migrated_count = ROW_COUNT;
        RAISE NOTICE 'Migrated % USD prices to price_usd', migrated_count;
        
        -- For properties without currency or with NULL currency, default to ETB
        UPDATE properties 
        SET price_etb = price 
        WHERE price IS NOT NULL 
          AND price > 0 
          AND (currency IS NULL OR currency = '') 
          AND (price_etb IS NULL OR price_etb = 0);
        
        GET DIAGNOSTICS migrated_count = ROW_COUNT;
        RAISE NOTICE 'Migrated % prices (no currency) to price_etb (default)', migrated_count;
        
    ELSE
        -- No currency column - migrate all prices to price_etb (default assumption)
        RAISE NOTICE 'Currency column does not exist. Migrating all prices to price_etb (default)...';
        
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'properties' AND column_name = 'price'
        ) THEN
            UPDATE properties 
            SET price_etb = price 
            WHERE price IS NOT NULL 
              AND price > 0 
              AND (price_etb IS NULL OR price_etb = 0);
            
            GET DIAGNOSTICS migrated_count = ROW_COUNT;
            RAISE NOTICE 'Migrated % prices to price_etb', migrated_count;
        END IF;
    END IF;
    
    -- Summary
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Migration Summary:';
    RAISE NOTICE '  Total properties: %', total_properties;
    
    SELECT COUNT(*) INTO migrated_count 
    FROM properties 
    WHERE price_etb IS NOT NULL AND price_etb > 0;
    RAISE NOTICE '  Properties with price_etb: %', migrated_count;
    
    SELECT COUNT(*) INTO migrated_count 
    FROM properties 
    WHERE price_usd IS NOT NULL AND price_usd > 0;
    RAISE NOTICE '  Properties with price_usd: %', migrated_count;
    
    SELECT COUNT(*) INTO migrated_count 
    FROM properties 
    WHERE (price_etb IS NULL OR price_etb = 0) 
      AND (price_usd IS NULL OR price_usd = 0);
    RAISE NOTICE '  Properties without prices: %', migrated_count;
    
    -- Add constraint if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'chk_properties_price' 
        AND table_name = 'properties'
    ) THEN
        RAISE NOTICE 'Adding price constraint...';
        ALTER TABLE properties 
        ADD CONSTRAINT chk_properties_price CHECK (
            (price_etb IS NOT NULL AND price_etb > 0) OR 
            (price_usd IS NOT NULL AND price_usd > 0)
        );
        RAISE NOTICE 'Price constraint added successfully.';
    ELSE
        RAISE NOTICE 'Price constraint already exists.';
    END IF;
    
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Migration completed successfully!';
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Error during migration: %', SQLERRM;
        RAISE;
END $$;

-- Optional: Show migration results
SELECT 
    COUNT(*) as total_properties,
    COUNT(CASE WHEN price_etb IS NOT NULL AND price_etb > 0 THEN 1 END) as with_price_etb,
    COUNT(CASE WHEN price_usd IS NOT NULL AND price_usd > 0 THEN 1 END) as with_price_usd,
    COUNT(CASE WHEN (price_etb IS NULL OR price_etb = 0) 
                   AND (price_usd IS NULL OR price_usd = 0) THEN 1 END) as without_prices
FROM properties;
