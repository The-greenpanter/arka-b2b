CREATE TABLE IF NOT EXISTS payments (
    payment_id   VARCHAR(36) PRIMARY KEY,
    order_id     VARCHAR(36) NOT NULL UNIQUE,
    amount       NUMERIC(19,2) NOT NULL DEFAULT 0,
    status       VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(255),
    created_at   TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
