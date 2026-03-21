-- Key/value site settings (e.g. public landing display timings). Values are stored as text (numeric strings).

CREATE TABLE site_settings (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO site_settings (setting_key, setting_value) VALUES
('SPONSOR_CAROUSEL_AUTOPLAY_MS', '10000'),
('SIDEBAR_MEDIA_ROTATION_MS', '12000'),
('SIDEBAR_LAYOUT_ROTATION_MS', '35000');
