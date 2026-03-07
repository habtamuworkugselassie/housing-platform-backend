-- Store registration/license numbers in parallel with document URLs
ALTER TABLE organizations
ADD COLUMN business_registration_number VARCHAR(255),
ADD COLUMN license_number VARCHAR(255),
ADD COLUMN vat_number VARCHAR(255),
ADD COLUMN tin_number VARCHAR(255);
