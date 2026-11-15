-- Language the visitor used when submitting (en / am); receipts are sent in it.
ALTER TABLE listing_enquiry ADD COLUMN IF NOT EXISTS locale VARCHAR(8);
ALTER TABLE exhibition_interest ADD COLUMN IF NOT EXISTS locale VARCHAR(8);
