-- V37 seeded the sponsorship package notes with the bank account holder written as
-- "Dream Team Trading PLC". The company is Dream Teams Trading PLC; fix the stored text.
-- REPLACE keeps the rest of each note (account number, other banks) exactly as it is.
UPDATE sponsorships
SET notes = REPLACE(notes, 'Dream Team Trading PLC', 'Dream Teams Trading PLC'),
    updated_at = CURRENT_TIMESTAMP
WHERE notes LIKE '%Dream Team Trading PLC%';
