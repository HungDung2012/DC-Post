BEGIN;

INSERT INTO order_statuses (status_code, status_name) VALUES
    ('PENDING', 'Chờ xác nhận'),
    ('CONFIRMED', 'Đã xác nhận'),
    ('PROCESSING', 'Đang xử lý'),
    ('SHIPPING', 'Đang giao hàng'),
    ('DELIVERED', 'Đã giao hàng'),
    ('CANCELLED', 'Đã hủy'),
    ('RETURNED', 'Đã hoàn hàng');

INSERT INTO shipment_statuses (status_code, status_name) VALUES
    ('CREATED', 'Đã tạo vận đơn'),
    ('IN_TRANSIT', 'Đang vận chuyển'),
    ('OUT_FOR_DELIVERY', 'Đang giao hàng'),
    ('DELIVERED', 'Đã giao hàng'),
    ('RETURNED', 'Đã hoàn hàng');

COMMIT;
