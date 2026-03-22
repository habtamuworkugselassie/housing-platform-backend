-- Ethio Build Connect Expo sponsorship tiers (የስፖንሰርሽፕ ፓኬጅ) — prices in ETB per official request letter.
-- Renames PREMIUM -> PLATINUM (1st tier / platinum); seeds Exclusive, Gold, Silver, Special.

UPDATE sponsorships SET type = 'PLATINUM', updated_at = CURRENT_TIMESTAMP WHERE type = 'PREMIUM';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'organizations'
          AND column_name = 'sponsorship_type'
    ) THEN
        EXECUTE 'UPDATE organizations SET sponsorship_type = ''PLATINUM'' WHERE sponsorship_type = ''PREMIUM''';
    END IF;
END $$;

UPDATE sponsorships
SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE name IN (
    'Basic Sponsorship',
    'Starter Package',
    'Enterprise Package',
    'Legacy Package'
);

UPDATE sponsorships
SET
    name = 'Ethio Build Connect — Platinum (1st tier)',
    description = 'Platinum (1st tier) sponsor for Ethio Build Connect Expo: five-day national real estate, construction & finance exhibition at Addis International Convention Center.',
    type = 'PLATINUM',
    base_price = 2200000.00,
    features = 'Recognition certificate (Ministry of Urban & Infrastructure, honorary minister signature) with special inscription; prime exhibition booth 27 m²; 2,500 brochures; indoor/outdoor screen ads at AICC during expo; front and inner ads on web and mobile (distinct placement); logo on related media promotions; VIP seating at discussion panels; 10 promotional banners at selected AICC high-traffic areas; 150 branded pens and 150 puzzles with your artwork; first-tier partner mention in entertainment program; publish organization ads and news with images/video on web and mobile for two years from expo date.',
    notes = 'Payment: 100% within 10 working days of signing. Bank (per letter): Commercial Bank of Ethiopia 1000314847467 — Dream Team Trading PLC; also Abay, Awash, Oromia Cooperative, Amhara, Dashn, Wegagen as listed in the official sponsorship letter.',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Premier Sponsorship';

INSERT INTO sponsorships (
    id, name, description, type, base_price, features, status, notes,
    created_at, updated_at, version, created_by, updated_by
)
SELECT
    gen_random_uuid(),
    'Ethio Build Connect — Platinum (1st tier)',
    'Platinum (1st tier) sponsor for Ethio Build Connect Expo: five-day national real estate, construction & finance exhibition at Addis International Convention Center.',
    'PLATINUM',
    2200000.00,
    'Recognition certificate (Ministry of Urban & Infrastructure, honorary minister signature) with special inscription; prime exhibition booth 27 m²; 2,500 brochures; indoor/outdoor screen ads at AICC during expo; front and inner ads on web and mobile (distinct placement); logo on related media promotions; VIP seating at discussion panels; 10 promotional banners at selected AICC high-traffic areas; 150 branded pens and 150 puzzles with your artwork; first-tier partner mention in entertainment program; publish organization ads and news with images/video on web and mobile for two years from expo date.',
    'ACTIVE',
    'Payment: 100% within 10 working days of signing. Bank (per letter): Commercial Bank of Ethiopia 1000314847467 — Dream Team Trading PLC; also Abay, Awash, Oromia Cooperative, Amhara, Dashn, Wegagen as listed in the official sponsorship letter.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sponsorships WHERE name = 'Ethio Build Connect — Platinum (1st tier)');

INSERT INTO sponsorships (
    id, name, description, type, base_price, features, status, notes,
    created_at, updated_at, version, created_by, updated_by
)
SELECT
    gen_random_uuid(),
    'Ethio Build Connect — Exclusive Sponsor',
    'Exclusive sponsor for Ethio Build Connect Expo: flagship national exhibition for real estate developers, banks, insurers, contractors, consultants, architects, materials suppliers, and finishing trades at Addis International Convention Center.',
    'EXCLUSIVE',
    5000000.00,
    'Recognition certificate (Ministry of Urban & Infrastructure) with special inscription; co-organizer rights for the event; organization logo on splash screen (web and mobile) before app content; prime booth 54 m²; 5,000 brochures; screen advertising inside and outside AICC during expo days; front and inner digital ads; opening ceremony speech alongside dignitaries and ministers; logo on media promotions; VIP seating at discussion panels; 20 promotional banners at AICC entrances and high-traffic areas; 300 pens and 300 puzzles with your images; listing in organizer directory on web and mobile; exclusive co-organizer/partner mention in entertainment program; publish ads and news with images and video for two years from expo date.',
    'ACTIVE',
    'Payment: 100% within 10 working days of signing. CBE account 1000314847467 (Dream Team Trading PLC); additional accounts per official letter.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sponsorships WHERE name = 'Ethio Build Connect — Exclusive Sponsor');

INSERT INTO sponsorships (
    id, name, description, type, base_price, features, status, notes,
    created_at, updated_at, version, created_by, updated_by
)
SELECT
    gen_random_uuid(),
    'Ethio Build Connect — Gold (2nd tier)',
    'Gold (2nd tier) sponsor package for Ethio Build Connect Expo.',
    'GOLD',
    1300000.00,
    'Prime booth 18 m²; 1,500 brochures; screen ads at AICC (frequency relative to exclusive/platinum tiers); inner ads on web and mobile; logo on media promotions; VIP seating at discussion panels; 6 promotional banners; 100 pens and 100 puzzles; second-tier partner mention in entertainment program; publish ads and news with images/video for two years from expo date.',
    'ACTIVE',
    'Payment: 100% within 10 working days of signing. See Platinum package notes for bank details.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sponsorships WHERE name = 'Ethio Build Connect — Gold (2nd tier)');

INSERT INTO sponsorships (
    id, name, description, type, base_price, features, status, notes,
    created_at, updated_at, version, created_by, updated_by
)
SELECT
    gen_random_uuid(),
    'Ethio Build Connect — Silver (3rd tier)',
    'Silver (3rd tier) sponsor package for Ethio Build Connect Expo.',
    'SILVER',
    350000.00,
    'Prime booth 9 m²; 500 brochures; VIP seating at discussion panels; 50 pens and 50 puzzles; partner mention in entertainment program; publish ads and news with images/video for two years from expo date.',
    'ACTIVE',
    'Payment: 100% within 10 working days of signing. See Platinum package notes for bank details.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sponsorships WHERE name = 'Ethio Build Connect — Silver (3rd tier)');

INSERT INTO sponsorships (
    id, name, description, type, base_price, features, status, notes,
    created_at, updated_at, version, created_by, updated_by
)
SELECT
    gen_random_uuid(),
    'Ethio Build Connect — Special participant',
    'Digital participation package for organizations that cannot attend the physical expo: full profile on web and mobile with logo, address, contacts, and photo/video story; self-managed credentials; visibility among peers in your sector.',
    'SPECIAL',
    150000.00,
    'Full digital listing: logo, office address, website, phone, email and contacts; narrative profile with photos and video; dedicated username/password to manage content and upload images/video; promote organization ads and news for two years from contract date.',
    'ACTIVE',
    'Payment: 100% within 10 working days of signing. CBE 1000314847467 (Dream Team Trading PLC); other banks per official letter.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sponsorships WHERE name = 'Ethio Build Connect — Special participant');
