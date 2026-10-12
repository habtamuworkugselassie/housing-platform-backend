-- Profile settings for buyers who signed up while ordering.
-- password_set: false = passwordless (quick registration / Google), true = has one,
-- NULL = older account where it was never recorded.
ALTER TABLE users ADD COLUMN password_set BOOLEAN;
ALTER TABLE users ADD COLUMN preferred_payment_method VARCHAR(32);
ALTER TABLE users ADD COLUMN payment_phone VARCHAR(32);
