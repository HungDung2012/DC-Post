# TÀI LIỆU ĐẶC TẢ GIAO DIỆN LẬP TRÌNH ỨNG DỤNG (API SPECIFICATION)
## Microservice: Quản Lý Đơn Hàng & Quản Lý Kho (`manager-product-and-warehouse`)

---

## 1. Quy Ước Chung (API Conventions)

* **Base URL:** `/api/v1`
* **Định dạng dữ liệu:** `application/json; charset=utf-8`
* **Xác thực (Authentication):** Sử dụng JWT Bearer Token trên HTTP Header:
  ```http
  Authorization: Bearer <access_token>
  ```
* **Cấu trúc Response Thành công chuẩn:**
  ```json
  {
    "success": true,
    "message": "Mô tả kết quả xử lý",
    "data": { ... },
    "meta": {
      "page": 1,
      "limit": 20,
      "total_items": 125,
      "total_pages": 7
    }
  }
  ```
* **Cấu trúc Response Lỗi chuẩn:**
  ```json
  {
    "success": false,
    "error": {
      "code": "ERR_INSUFFICIENT_STOCK",
      "message": "Số lượng hàng tồn kho khả dụng không đủ cho sản phẩm PRD-001",
      "details": {
        "product_id": 12,
        "requested": 10,
        "available": 4
      }
    }
  }
  ```

---

## 2. Đặc Tả Chi Tiết API: UC01 - Quản Lý Đơn Hàng (Order, Payment, Refund, Shipment)

### 2.1. Tạo đơn hàng mới (Kèm giữ chỗ tồn kho & khởi tạo thanh toán)
* **Endpoint:** `POST /api/v1/orders`
* **Quyền truy cập:** `CUSTOMER`, `STAFF`, `ADMIN`
* **Mô tả:** Khách hàng tự đặt đơn hoặc Nhân viên bưu cục tạo đơn tại quầy. Hệ thống kiểm tra tồn kho khả dụng (`quantity - reserved_quantity`), giữ chỗ tồn kho (`RESERVE`), tạo đơn hàng (`orders`, `order_items`), lưu lịch sử trạng thái (`order_status_history`) và tạo bản ghi thanh toán (`payments`).

#### Request Body:
```json
{
  "custom_id": 105,
  "warehouse_id": 1,
  "payment_method": "COD",
  "items": [
    {
      "product_id": 12,
      "quantity": 2
    },
    {
      "product_id": 15,
      "quantity": 1
    }
  ]
}
```
*(Lưu ý: Nếu người gọi là `CUSTOMER`, hệ thống tự lấy `custom_id` từ JWT Token; nếu là `STAFF`, hệ thống lấy `custom_id` từ body).*

#### Response `201 Created`:
```json
{
  "success": true,
  "message": "Tạo đơn hàng thành công",
  "data": {
    "order_id": 501,
    "order_code": "ORD-20261124-0501",
    "custom_id": 105,
    "warehouse_id": 1,
    "status": {
      "status_id": 1,
      "status_code": "PENDING",
      "status_name": "Chờ xác nhận"
    },
    "total_amount": 450000.00,
    "payment_method": "COD",
    "created_at": "2026-11-24T01:00:00Z",
    "items": [
      {
        "order_item_id": 901,
        "product_id": 12,
        "item_name": "Thùng carton tiêu chuẩn loại A",
        "quantity": 2,
        "unit_price": 150000.00
      },
      {
        "order_item_id": 902,
        "product_id": 15,
        "item_name": "Túi chống sốc cao cấp",
        "quantity": 1,
        "unit_price": 150000.00
      }
    ],
    "payment": {
      "payment_id": 301,
      "amount": 450000.00,
      "payment_method": "COD",
      "payment_status": "PENDING",
      "transaction_code": null,
      "paid_at": null
    }
  }
}
```

---

### 2.2. Lấy danh sách đơn hàng (Tìm kiếm & Phân trang)
* **Endpoint:** `GET /api/v1/orders`
* **Quyền truy cập:** `CUSTOMER` (chỉ thấy đơn của chính mình), `STAFF`, `ADMIN`
* **Query Parameters:**
  - `page` (int, default `1`), `limit` (int, default `20`)
  - `status_code` (string, optional): `PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPING`, `DELIVERED`, `CANCELLED`
  - `order_code` (string, optional): Tìm theo mã đơn hàng
  - `custom_id` (int, optional - dành cho `STAFF`/`ADMIN`)
  - `from_date`, `to_date` (YYYY-MM-DD, optional)

---

### 2.3. Xem chi tiết đơn hàng & Trạng thái hiện tại
* **Endpoint:** `GET /api/v1/orders/:order_id`
* **Quyền truy cập:** `CUSTOMER`, `STAFF`, `ADMIN`
* **Mô tả:** Trả về toàn bộ thông tin đơn hàng, danh sách sản phẩm (`order_items`), thông tin thanh toán (`payments`), hoàn tiền (`refunds` nếu có), danh sách vận đơn (`shipments`) và lịch sử trạng thái (`order_status_history`).

---

### 2.4. Cập nhật trạng thái đơn hàng (Quản lý trạng thái đơn hàng)
* **Endpoint:** `PATCH /api/v1/orders/:order_id/status`
* **Quyền truy cập:** `STAFF` (được phân công tại `order.warehouse_id`), `ADMIN`
* **Mô tả:** Chuyển trạng thái duyệt đơn theo State Machine (`PENDING -> CONFIRMED -> PROCESSING`) và tự động ghi vết vào `order_status_history`.
  - *(Lưu ý: Cấm gọi API này để chuyển sang `SHIPPING`, `DELIVERED`, `RETURNED` hoặc `CANCELLED` — trả về `422 ERR_INVALID_STATUS_TRANSITION` và bắt buộc dùng đúng endpoint chuyên biệt để đảm bảo đồng bộ kho/vận đơn/hoàn tiền).*

#### Request Body:
```json
{
  "new_status_code": "CONFIRMED"
}
```

#### Response `200 OK`:
```json
{
  "success": true,
  "message": "Cập nhật trạng thái đơn hàng thành công",
  "data": {
    "order_id": 501,
    "order_code": "ORD-20261124-0501",
    "old_status_code": "PENDING",
    "new_status_code": "CONFIRMED",
    "changed_by": 22,
    "change_at": "2026-11-24T01:15:00Z"
  }
}
```

---

### 2.5. Xem lịch sử thay đổi trạng thái đơn hàng
* **Endpoint:** `GET /api/v1/orders/:order_id/status-history`
* **Quyền truy cập:** `CUSTOMER`, `STAFF`, `ADMIN`
* **Response `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "history_id": 1,
      "order_id": 501,
      "old_status": null,
      "new_status": { "status_id": 1, "status_code": "PENDING", "status_name": "Chờ xác nhận" },
      "changed_by": 105,
      "change_at": "2026-11-24T01:00:00Z"
    },
    {
      "history_id": 2,
      "order_id": 501,
      "old_status": { "status_id": 1, "status_code": "PENDING", "status_name": "Chờ xác nhận" },
      "new_status": { "status_id": 2, "status_code": "CONFIRMED", "status_name": "Đã xác nhận" },
      "changed_by": 22,
      "change_at": "2026-11-24T01:15:00Z"
    }
  ]
}
```

---

### 2.6. Hủy đơn hàng & Tự động kích hoạt Hoàn tiền (`<<extend>>`)
* **Endpoint:** `POST /api/v1/orders/:order_id/cancel`
* **Quyền truy cập:** `CUSTOMER`, `STAFF`, `ADMIN`
* **Mô tả:**
  1. Kiểm tra đơn hàng đang ở trạng thái `PENDING` hoặc `CONFIRMED`.
  2. Chuyển trạng thái đơn sang `CANCELLED` và ghi `order_status_history`.
  3. Giải phóng số lượng tồn kho giữ chỗ (`reserved_quantity`) và ghi `inventory_transactions` (`RELEASE`).
  4. Nếu đơn đã thanh toán (`payment_status == 'PAID'`), tự động sinh bản ghi hoàn tiền trong bảng `refunds`.

#### Response `200 OK`:
```json
{
  "success": true,
  "message": "Hủy đơn hàng thành công",
  "data": {
    "order_id": 501,
    "status_code": "CANCELLED",
    "stock_released": true,
    "refund": {
      "refund_id": 10,
      "payment_id": 301,
      "refund_amount": 450000.00,
      "refund_status": "PENDING",
      "created_at": "2026-11-24T01:20:00Z"
    }
  }
}
```

---

### 2.7. Xác nhận Thanh toán & Xử lý Hoàn tiền
* **`POST /api/v1/orders/:order_id/payment/confirm`** (`STAFF`, `ADMIN`, hoặc Payment Webhook):
  - Request Body: `{ "transaction_code": "VNPAY-99887766" }`
  - Cập nhật `payments.payment_status = 'PAID'`, `paid_at = NOW()`.
* **`PATCH /api/v1/refunds/:refund_id/status`** (`STAFF`, `ADMIN`):
  - Request Body: `{ "refund_status": "COMPLETED" }`
  - Cập nhật trạng thái hoàn tiền trong bảng `refunds` và đổi `payments.payment_status = 'REFUNDED'`.

---

### 2.8. Tạo Vận đơn & Xuất Kho Giao hàng (`shipments`)
* **Endpoint:** `POST /api/v1/shipments`
* **Quyền truy cập:** `STAFF` (được phân công tại `order.warehouse_id`), `ADMIN`
* **Mô tả:** Quy tắc **1 Đơn hàng = 1 Vận đơn trọn gói**. Hệ thống tự động lấy `order.warehouse_id` và toàn bộ `order_items` của đơn hàng (đang ở trạng thái `CONFIRMED` hoặc `PROCESSING`) để đóng gói sang `shipment_items`, trừ tồn kho thực tế (`quantity -= qty`, `reserved_quantity -= qty`), ghi `inventory_transactions` (`EXPORT`), chuyển đơn hàng sang `SHIPPING` và ghi sự kiện đầu tiên vào `shipment_tracking_events`.

#### Request Body:
```json
{
  "order_id": 501,
  "shipper_id": 45
}
```
*(Hệ thống tự tính `cod_amount = order.total_amount` nếu `payment_method == 'COD'` và chưa thanh toán, ngược lại `cod_amount = 0`).*

#### Response `201 Created`:
```json
{
  "success": true,
  "message": "Tạo vận đơn và xuất kho thành công",
  "data": {
    "shipment_id": 701,
    "order_id": 501,
    "tracking_code": "TRK-20261124-0701",
    "shipper_id": 45,
    "status": {
      "status_id": 1,
      "status_code": "CREATED",
      "status_name": "Đã tạo vận đơn & Xuất kho"
    },
    "current_warehouse_id": 1,
    "cod_amount": 450000.00,
    "delivered_at": null
  }
}
```

---

### 2.9. Quét mã cập nhật hành trình Vận đơn & Xem Lịch sử Giao hàng
* **`POST /api/v1/shipments/:shipment_id/tracking-events`** (`STAFF`, `ADMIN`):
  - Ghi nhận kiện hàng di chuyển giữa các kho hoặc kết thúc giao hàng.
  - **Đồng bộ tự động:**
    - Nếu `status_code == "DELIVERED"`: cập nhật `shipments.delivered_at = NOW()`, tự động chuyển `orders.status = DELIVERED` (ghi `order_status_history`), và nếu đơn `COD` thì cập nhật `payments.payment_status = 'PAID'`, `paid_at = NOW()`.
    - Nếu `status_code == "RETURNED"`: tự động chuyển `orders.status = RETURNED` (ghi `order_status_history`), cộng hoàn tồn kho tại `warehouse_id` (`quantity += qty`) và ghi `inventory_transactions` (`RETURN`, gắn `shipment_id`).
  - Request Body:
    ```json
    {
      "status_code": "IN_TRANSIT",
      "from_warehouse_id": 1,
      "warehouse_id": 2
    }
    ```
* **`GET /api/v1/shipments/tracking/:tracking_code`** (`CUSTOMER`, `STAFF`, `ADMIN`):
  - Khách hàng **Xem lịch sử giao hàng** chi tiết theo dòng thời gian (`shipment_tracking_events`).
  - Response `200 OK`:
    ```json
    {
      "success": true,
      "data": {
        "shipment_id": 701,
        "tracking_code": "TRK-20261124-0701",
        "order_id": 501,
        "current_status": "IN_TRANSIT",
        "current_warehouse": { "warehouse_id": 2, "warehouse_name": "Kho Trung Chuyển Đà Nẵng" },
        "delivered_at": null,
        "events": [
          {
            "event_id": 1002,
            "status_code": "IN_TRANSIT",
            "status_name": "Đang luân chuyển",
            "from_warehouse_id": 1,
            "warehouse_id": 2,
            "scanned_by": 30,
            "scanned_at": "2026-11-24T05:30:00Z"
          },
          {
            "event_id": 1001,
            "status_code": "CREATED",
            "status_name": "Đã xuất kho gốc",
            "from_warehouse_id": null,
            "warehouse_id": 1,
            "scanned_by": 22,
            "scanned_at": "2026-11-24T01:30:00Z"
          }
        ]
      }
    }
    ```

---

## 3. Đặc Tả Chi Tiết API: UC02 - Quản Lý Kho (Warehouses, Products, Inventory, Transactions)

### 3.1. Nhóm API Quản lý Sản phẩm (`products`)
| Method | Endpoint | Role | Request Body / Params | Mô tả |
| :---: | :--- | :---: | :--- | :--- |
| `POST` | `/api/v1/products` | `STAFF`, `ADMIN` | `{ "product_code": "PRD-01", "product_name": "...", "unit_price": 150000 }` | Tạo sản phẩm mới |
| `GET` | `/api/v1/products` | Tất cả | `?search=&page=1&limit=20` | Danh sách sản phẩm |
| `GET` | `/api/v1/products/:id` | Tất cả | Path `:id` | Chi tiết 1 sản phẩm |
| `PUT` | `/api/v1/products/:id` | `STAFF`, `ADMIN` | `{ "product_name": "...", "unit_price": 160000 }` | Cập nhật sản phẩm |

---

### 3.2. Nhóm API Quản lý Kho & Nhân viên Kho (`warehouses`, `warehouse_staff`)
| Method | Endpoint | Role | Request Body / Params | Mô tả |
| :---: | :--- | :---: | :--- | :--- |
| `POST` | `/api/v1/warehouses` | `ADMIN` | `{ "warehouse_name": "Kho Bưu Cục Quận 1", "address": "123 Lê Lợi..." }` | Tạo kho / bưu cục mới |
| `GET` | `/api/v1/warehouses` | `STAFF`, `ADMIN` | `?page=1&limit=20` | Danh sách tất cả kho |
| `PUT` | `/api/v1/warehouses/:id` | `ADMIN` | `{ "warehouse_name": "...", "address": "..." }` | Cập nhật thông tin kho |
| `POST` | `/api/v1/warehouses/:id/staff` | `ADMIN` | `{ "user_id": 22 }` | Phân công nhân viên vào kho (`warehouse_staff`) |
| `DELETE`| `/api/v1/warehouses/:id/staff/:user_id` | `ADMIN` | Path `:id`, `:user_id` | Gỡ nhân viên khỏi kho |

---

### 3.3. Xem danh sách Hàng Tồn Kho (`UC02.2 - Quản lý hàng tồn kho`)
* **Endpoint:** `GET /api/v1/warehouses/:warehouse_id/inventory`
* **Quyền truy cập:** `STAFF`, `ADMIN`
* **Query Parameters:** `search` (lọc theo mã/tên sản phẩm), `low_stock=true` (lọc hàng sắp hết), `page`, `limit`
* **Response `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "inventory_id": 1,
      "warehouse_id": 1,
      "warehouse_name": "Kho Bưu Cục Trung Tâm Hà Nội",
      "product": {
        "product_id": 12,
        "product_code": "PRD-001",
        "product_name": "Thùng carton tiêu chuẩn loại A",
        "unit_price": 150000.00
      },
      "quantity": 500,
      "reserved_quantity": 25,
      "available_quantity": 475
    }
  ]
}
```

---

### 3.4. Nhập hàng vào Kho (`IMPORT`)
* **Endpoint:** `POST /api/v1/inventory/import`
* **Quyền truy cập:** `STAFF`, `ADMIN`
* **Mô tả:** Tăng `inventory.quantity` tại kho chỉ định và ghi nhận lịch sử vào `inventory_transactions` (`transaction_type = 'IMPORT'`, `performed_by = user_id`).

#### Request Body:
```json
{
  "warehouse_id": 1,
  "items": [
    {
      "product_id": 12,
      "quantity": 100
    },
    {
      "product_id": 15,
      "quantity": 50
    }
  ]
}
```

#### Response `200 OK`:
```json
{
  "success": true,
  "message": "Nhập kho thành công",
  "data": {
    "warehouse_id": 1,
    "performed_by": 22,
    "transactions": [
      {
        "transaction_id": 5001,
        "product_id": 12,
        "transaction_type": "IMPORT",
        "quantity": 100,
        "new_stock_quantity": 600,
        "created_at": "2026-11-24T02:00:00Z"
      },
      {
        "transaction_id": 5002,
        "product_id": 15,
        "transaction_type": "IMPORT",
        "quantity": 50,
        "new_stock_quantity": 150,
        "created_at": "2026-11-24T02:00:00Z"
      }
    ]
  }
}
```

---

### 3.5. Xuất hàng khỏi Kho thủ công (`EXPORT`) & Kiểm kê Kho (`ADJUST`)
* **`POST /api/v1/inventory/export`** (`STAFF` thuộc kho, `ADMIN`):
  - **Chỉ dùng cho xuất kho nội bộ / xuất hủy hàng hỏng** (`shipment_id` luôn lưu `NULL`, tránh trừ trùng kho với `POST /api/v1/shipments`).
  - Kiểm tra `quantity - reserved_quantity >= export_qty` trước khi giảm `quantity` và ghi `inventory_transactions` (`EXPORT`).
  - Request Body:
    ```json
    {
      "warehouse_id": 1,
      "items": [
        { "product_id": 12, "quantity": 10 }
      ]
    }
    ```
* **`POST /api/v1/inventory/adjust`** (`STAFF` thuộc kho, `ADMIN`):
  - Điều chỉnh số lượng tồn kho thực tế khi kiểm kê định kỳ.
  - Nếu `actual_quantity < reserved_quantity`, hệ thống từ chối và trả về `422 ERR_BELOW_RESERVED_STOCK`.
  - Request Body:
    ```json
    {
      "warehouse_id": 1,
      "product_id": 12,
      "actual_quantity": 588
    }
    ```

---

### 3.6. Xem Lịch sử Hàng hóa Ra/Vào Kho (`UC02.3 - inventory_transactions`)
* **Endpoint:** `GET /api/v1/inventory/transactions`
* **Quyền truy cập:** `STAFF`, `ADMIN`
* **Query Parameters:**
  - `warehouse_id` (int, optional): Lọc theo kho
  - `product_id` (int, optional): Lọc theo sản phẩm
  - `shipment_id` (int, optional): Lọc theo vận đơn
  - `transaction_type` (string, optional): `IMPORT`, `EXPORT`, `RESERVE`, `RELEASE`, `RETURN`, `ADJUST`
  - `performed_by` (int, optional): Lọc theo nhân viên thực hiện
  - `from_date`, `to_date` (YYYY-MM-DD, optional)
  - `page` (default `1`), `limit` (default `20`)

#### Response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "transaction_id": 5005,
      "warehouse": {
        "warehouse_id": 1,
        "warehouse_name": "Kho Bưu Cục Trung Tâm Hà Nội"
      },
      "product": {
        "product_id": 12,
        "product_code": "PRD-001",
        "product_name": "Thùng carton tiêu chuẩn loại A"
      },
      "shipment_id": 701,
      "performed_by": 22,
      "transaction_type": "EXPORT",
      "quantity": -2,
      "created_at": "2026-11-24T01:30:00Z"
    },
    {
      "transaction_id": 5001,
      "warehouse": {
        "warehouse_id": 1,
        "warehouse_name": "Kho Bưu Cục Trung Tâm Hà Nội"
      },
      "product": {
        "product_id": 12,
        "product_code": "PRD-001",
        "product_name": "Thùng carton tiêu chuẩn loại A"
      },
      "shipment_id": null,
      "performed_by": 22,
      "transaction_type": "IMPORT",
      "quantity": 100,
      "created_at": "2026-11-24T01:00:00Z"
    }
  ],
  "meta": {
    "page": 1,
    "limit": 20,
    "total_items": 2,
    "total_pages": 1
  }
}
```

---

## 4. Bảng Mã Lỗi Hệ Thống Chuẩn (Standard Error Codes)

| HTTP Status | Mã lỗi (`error.code`) | Ý nghĩa & Nguyên nhân |
| :---: | :--- | :--- |
| `400` | `ERR_INVALID_REQUEST` | Dữ liệu đầu vào thiếu trường bắt buộc hoặc sai định dạng JSON. |
| `400` | `ERR_INSUFFICIENT_STOCK` | Số lượng tồn kho khả dụng (`quantity - reserved_quantity`) không đủ để tạo đơn hoặc xuất kho. |
| `401` | `ERR_UNAUTHORIZED` | Thiếu JWT Token hoặc Token đã hết hạn. |
| `403` | `ERR_FORBIDDEN` | Người dùng không có quyền thực hiện thao tác (VD: `CUSTOMER` gọi API nhập kho). |
| `403` | `ERR_WAREHOUSE_ACCESS_DENIED` | Nhân viên `STAFF` thao tác trên `warehouse_id` không nằm trong danh sách phân công `warehouse_staff`. |
| `404` | `ERR_RESOURCE_NOT_FOUND` | Không tìm thấy `order_id`, `warehouse_id`, `product_id`, hoặc `tracking_code`. |
| `409` | `ERR_DUPLICATE_CODE` | Trùng mã `product_code`, `order_code`, hoặc `tracking_code`. |
| `422` | `ERR_INVALID_STATUS_TRANSITION` | Chuyển trạng thái đơn hàng hoặc hủy đơn hàng trái với quy tắc State Machine. |
| `422` | `ERR_BELOW_RESERVED_STOCK` | Số lượng kiểm kê thực tế (`actual_quantity`) nhỏ hơn số lượng đang giữ chỗ (`reserved_quantity`). |
| `500` | `ERR_INTERNAL_SERVER` | Lỗi hệ thống hoặc lỗi kết nối PostgreSQL. |
