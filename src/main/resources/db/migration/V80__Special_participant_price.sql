-- The Special Participant package is 100,000 ETB in the sponsorship sheet and agreement; the
-- seed had 150,000. Align the site with the printed documents (only if nobody changed it since).
UPDATE sponsorships
   SET base_price = 100000.00,
       updated_at = CURRENT_TIMESTAMP
 WHERE type = 'SPECIAL'
   AND base_price = 150000.00;
