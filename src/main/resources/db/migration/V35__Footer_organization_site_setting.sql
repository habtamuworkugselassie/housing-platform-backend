-- Public footer contact is resolved from this organization registration (see DisplaySettingsService).
INSERT INTO site_settings (setting_key, setting_value) VALUES
    ('FOOTER_ORGANIZATION_REGISTRATION_NUMBER', 'DTT-PLC-FOOTER-001')
ON CONFLICT (setting_key) DO NOTHING;
