CREATE TABLE app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP,
    updated_by    UUID REFERENCES users(user_id)
);

INSERT INTO app_settings (setting_key, setting_value)
VALUES ('approval_threshold', '100');
