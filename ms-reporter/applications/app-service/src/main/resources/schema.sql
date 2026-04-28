CREATE TABLE IF NOT EXISTS domain_events (
    event_id    VARCHAR(36) PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    payload     TEXT,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_domain_events_type ON domain_events(event_type);
CREATE INDEX IF NOT EXISTS idx_domain_events_aggregate ON domain_events(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_domain_events_occurred ON domain_events(occurred_at);
