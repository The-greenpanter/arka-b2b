CREATE TABLE IF NOT EXISTS providers (
    provider_id   VARCHAR(36) PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255),
    phone         VARCHAR(50),
    lead_time_days INTEGER,
    active        BOOLEAN DEFAULT true,
    created_at    TIMESTAMPTZ
);
