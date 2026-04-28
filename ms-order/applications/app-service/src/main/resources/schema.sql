CREATE TABLE IF NOT EXISTS orders (
    order_id   VARCHAR(36) PRIMARY KEY,
    cart_id    VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    status     VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
