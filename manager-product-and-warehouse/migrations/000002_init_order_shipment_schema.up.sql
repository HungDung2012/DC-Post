BEGIN;

CREATE TABLE order_statuses (
    status_id SERIAL PRIMARY KEY,
    status_code VARCHAR(255) UNIQUE NOT NULL,
    status_name VARCHAR(255) NOT NULL
);

CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    order_code VARCHAR(255) UNIQUE NOT NULL,
    custom_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    status_id INTEGER NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE order_items (
    order_item_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(15, 2) NOT NULL
);

CREATE TABLE order_status_history (
    history_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL,
    old_status_id INTEGER NULL,
    new_status_id INTEGER NOT NULL,
    changed_by INTEGER NOT NULL,
    change_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payments (
    payment_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    payment_status VARCHAR(255) NOT NULL,
    transaction_code VARCHAR(255) NULL,
    paid_at TIMESTAMPTZ NULL
);

CREATE TABLE refunds (
    refund_id SERIAL PRIMARY KEY,
    payment_id INTEGER NOT NULL,
    refund_amount DECIMAL(15, 2) NOT NULL,
    refund_status VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE shipment_statuses (
    status_id SERIAL PRIMARY KEY,
    status_code VARCHAR(255) UNIQUE NOT NULL,
    status_name VARCHAR(255) NOT NULL
);

CREATE TABLE shipments (
    shipment_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL,
    tracking_code VARCHAR(255) UNIQUE NOT NULL,
    shipper_id INTEGER NULL,
    status_id INTEGER NOT NULL,
    current_warehouse_id INTEGER NULL,
    cod_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    delivered_at TIMESTAMPTZ NULL
);

CREATE TABLE shipment_items (
    shipment_item_id SERIAL PRIMARY KEY,
    shipment_id INTEGER NOT NULL,
    order_item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL
);

CREATE TABLE shipment_tracking_events (
    event_id SERIAL PRIMARY KEY,
    shipment_id INTEGER NOT NULL,
    status_id INTEGER NOT NULL,
    warehouse_id INTEGER NULL,
    scanned_by INTEGER NOT NULL,
    from_warehouse_id INTEGER NULL,
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_custom_id_created_at ON orders (custom_id, created_at DESC);
CREATE INDEX idx_orders_status_id ON orders (status_id);
CREATE INDEX idx_shipment_events_shipment_scanned ON shipment_tracking_events (shipment_id, scanned_at DESC);

COMMIT;
