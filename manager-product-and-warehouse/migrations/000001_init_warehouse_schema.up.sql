BEGIN;

CREATE TABLE products (
    product_id SERIAL PRIMARY KEY,
    product_code VARCHAR(255) UNIQUE NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(15, 2) NOT NULL
);

CREATE TABLE warehouses (
    warehouse_id SERIAL PRIMARY KEY,
    warehouse_name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL
);

CREATE TABLE warehouse_staff (
    warehouse_id INTEGER,
    user_id INTEGER,
    PRIMARY KEY (warehouse_id, user_id)
);

CREATE TABLE inventory (
    inventory_id SERIAL PRIMARY KEY,
    warehouse_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_warehouse_product UNIQUE (warehouse_id, product_id)
);

CREATE TABLE inventory_transactions (
    transaction_id SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    shipment_id INTEGER NULL,
    performed_by INTEGER NOT NULL,
    transaction_type VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inv_tx_warehouse_created ON inventory_transactions (warehouse_id, created_at DESC);
CREATE INDEX idx_inv_tx_product_id ON inventory_transactions (product_id);

COMMIT;
