-- External warehouse/actor IDs are provisionally INT by owner decision (2026-11-24).
-- Confirm actual identifier types at integration; no cross-service foreign keys.
CREATE TABLE shipments (
    shipment_id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NULL,
    tracking_code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sender_name VARCHAR(255) NOT NULL,
    sender_phone VARCHAR(20) NOT NULL,
    sender_address VARCHAR(500) NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    recipient_address VARCHAR(500) NOT NULL,
    weight DECIMAL(10,3) NOT NULL,
    length DECIMAL(10,2) NOT NULL,
    width DECIMAL(10,2) NOT NULL,
    height DECIMAL(10,2) NOT NULL,
    shipping_fee DECIMAL(15,2) NOT NULL,
    pickup_method VARCHAR(20) NOT NULL,
    creation_channel VARCHAR(20) NOT NULL,
    drop_off_warehouse_id INT NULL,
    created_by INT NOT NULL,
    note TEXT NULL,
    cancel_reason VARCHAR(500) NULL,
    cancelled_at DATETIME(6) NULL,
    cancelled_by INT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- Preserve the selected payment method before any payment attempt exists.
    payment_method VARCHAR(20) NOT NULL,
    -- Scoped request identity and canonical payload hash prevent duplicate creates.
    creation_request_scope VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creation_request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creation_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by_source VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    -- Scheduler state belongs to this shipment; no integration task table is needed.
    sync_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    sync_attempt_count INT NOT NULL DEFAULT 0,
    sync_next_attempt_at DATETIME(6) NULL,
    sync_started_at DATETIME(6) NULL,
    sync_last_error_code VARCHAR(64) NULL,
    -- Cancellation confirmation is separate from authoritative shipping status.
    cancellation_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    cancellation_source VARCHAR(20) NULL,
    cancellation_request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    cancellation_next_check_at DATETIME(6) NULL,
    PRIMARY KEY (shipment_id),
    CONSTRAINT uq_shipments_tracking UNIQUE (tracking_code),
    CONSTRAINT uq_shipments_creation_request UNIQUE (creation_request_scope, creation_request_key),
    CONSTRAINT fk_shipments_customer FOREIGN KEY (customer_id) REFERENCES users (user_id),
    CONSTRAINT ck_shipments_channel CHECK (creation_channel IN ('ONLINE', 'COUNTER')),
    CONSTRAINT ck_shipments_customer CHECK (creation_channel <> 'ONLINE' OR customer_id IS NOT NULL),
    CONSTRAINT ck_shipments_pickup CHECK (pickup_method IN ('HOME_PICKUP', 'DROP_OFF')),
    CONSTRAINT ck_shipments_counter CHECK (creation_channel <> 'COUNTER' OR pickup_method = 'DROP_OFF'),
    CONSTRAINT ck_shipments_location CHECK (
        (pickup_method = 'DROP_OFF' AND drop_off_warehouse_id IS NOT NULL AND drop_off_warehouse_id > 0)
        OR (pickup_method = 'HOME_PICKUP' AND drop_off_warehouse_id IS NULL)
    ),
    CONSTRAINT ck_shipments_parcel CHECK (weight > 0 AND length > 0 AND width > 0 AND height > 0),
    CONSTRAINT ck_shipments_fee CHECK (shipping_fee >= 0 AND shipping_fee = FLOOR(shipping_fee)),
    CONSTRAINT ck_shipments_actor CHECK (created_by > 0 AND (cancelled_by IS NULL OR cancelled_by > 0)),
    CONSTRAINT ck_shipments_payment CHECK (payment_method IN ('CASH', 'VNPAY')),
    CONSTRAINT ck_shipments_sync CHECK (sync_status IN ('WAITING_PAYMENT', 'PENDING', 'PROCESSING', 'SYNCED', 'UNKNOWN', 'BLOCKED', 'CANCELLED')),
    CONSTRAINT ck_shipments_attempts CHECK (sync_attempt_count >= 0),
    CONSTRAINT ck_shipments_cancellation CHECK (cancellation_status IN ('NONE', 'REQUESTED', 'UNKNOWN', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_shipments_cancel_source CHECK (cancellation_source IS NULL OR cancellation_source IN ('LOCAL', 'EXTERNAL')),
    INDEX ix_shipments_customer_created (customer_id, created_at, shipment_id),
    INDEX ix_shipments_sync_schedule (sync_status, sync_next_attempt_at),
    INDEX ix_shipments_cancel_schedule (cancellation_status, cancellation_next_check_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shipment_items (
    shipment_item_id BIGINT NOT NULL AUTO_INCREMENT,
    shipment_id BIGINT NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    item_category VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    declared_value DECIMAL(15,2) NOT NULL,
    note VARCHAR(500) NULL,
    PRIMARY KEY (shipment_item_id),
    CONSTRAINT fk_shipment_items_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (shipment_id),
    CONSTRAINT ck_shipment_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_shipment_items_value CHECK (declared_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
