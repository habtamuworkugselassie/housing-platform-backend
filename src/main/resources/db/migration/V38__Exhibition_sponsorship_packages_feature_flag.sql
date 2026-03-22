-- Feature flag: show/hide sponsorship packages section on exhibition landing (admin Display settings).

INSERT INTO site_settings (setting_key, setting_value, updated_at)
VALUES ('EXHIBITION_SPONSORSHIP_PACKAGES_VISIBLE', 'true', CURRENT_TIMESTAMP)
ON CONFLICT (setting_key) DO NOTHING;
