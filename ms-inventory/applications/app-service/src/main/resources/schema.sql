-- Schema para ms-inventory: tabla de Stock y movimientos (para auditoría futura)

-- Tabla principal de Stock
CREATE TABLE IF NOT EXISTS stock (
    stock_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL UNIQUE,
    provider_id VARCHAR(36),
    available_qty INTEGER NOT NULL DEFAULT 0,
    reserved_qty INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DEPLETED')),
    minimum_threshold INTEGER NOT NULL DEFAULT 10,
    alert_sent BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Índice para búsquedas por productId (muy común)
CREATE INDEX IF NOT EXISTS idx_stock_product_id ON stock(product_id);

-- HU3 migrations: add columns to existing tables (safe for re-runs)
ALTER TABLE stock ADD COLUMN IF NOT EXISTS provider_id VARCHAR(36);
ALTER TABLE stock ADD COLUMN IF NOT EXISTS minimum_threshold INTEGER NOT NULL DEFAULT 10;
ALTER TABLE stock ADD COLUMN IF NOT EXISTS alert_sent BOOLEAN NOT NULL DEFAULT false;

-- Tabla de auditoría: registro de cada movimiento
CREATE TABLE IF NOT EXISTS stock_movement (
    movement_id VARCHAR(36) PRIMARY KEY,
    stock_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN ('RESERVE', 'RELEASE', 'CONSUME', 'RESTOCK')),
    quantity INTEGER NOT NULL,
    reason VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    FOREIGN KEY (stock_id) REFERENCES stock(stock_id) ON DELETE CASCADE
);

-- Índices para análisis de auditoría
CREATE INDEX IF NOT EXISTS idx_movement_stock_id ON stock_movement(stock_id);
CREATE INDEX IF NOT EXISTS idx_movement_timestamp ON stock_movement(timestamp);
