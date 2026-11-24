# TÀI LIỆU KIẾN TRÚC MÃ NGUỒN & CẤU TRÚC THƯ MỤC (FOLDER & CODE ARCHITECTURE)
## Microservice: Quản Lý Đơn Hàng & Quản Lý Kho (`manager-product-and-warehouse`)

---

## 1. Nguyên Tắc Thiết Kế Mã Nguồn (Architectural Principles)

Phân hệ `manager-product-and-warehouse` được thiết kế theo chuẩn **Standard Go Project Layout** kết hợp với **Clean Architecture (Layered Architecture)**:

1. **Cô lập hoàn toàn trong 1 thư mục (`manager-product-and-warehouse`):**
   - Giúp nhóm 3 người làm việc độc lập trên cùng 1 Git Repository (Monorepo chứa nhiều Microservice) mà không bao giờ bị xung đột file (Merge Conflict).
   - Có `go.mod`, `Dockerfile`, `docker-compose.yml` và thư mục `migrations/` riêng biệt.
2. **Sự phụ thuộc một chiều (The Dependency Rule):**
   $$\text{Delivery (HTTP Handler)} \longrightarrow \text{Usecase (Business Logic)} \longrightarrow \text{Domain (Entities \& Interfaces)} \longleftarrow \text{Repository (PostgreSQL)}$$
   - Tầng `domain` nằm ở trung tâm và không phụ thuộc vào bất kỳ thư viện bên ngoài nào.
   - Tầng `usecase` chỉ gọi xuống database thông qua các `Repository Interface` định nghĩa ở tầng `domain`.

---

## 2. Sơ Đồ Cây Thư Mục Chi Tiết (Directory Tree)

```text
DC-Post/
└── manager-product-and-warehouse/              # Thư mục gốc của Microservice (2 UC của bạn)
    ├── README.md                               # Hướng dẫn tổng quan, cài đặt và danh mục tài liệu
    ├── go.mod                                  # Khai báo Go module và dependencies
    ├── go.sum
    ├── .env.example                            # Mẫu cấu hình biến môi trường
    ├── Dockerfile                              # Đóng gói container cho Microservice
    ├── docker-compose.yml                      # Khởi chạy nhanh PostgreSQL + Service
    ├── Makefile                                # Tập hợp các lệnh tự động hóa (build, run, test, migrate)
    │
    ├── docs/                                   # [BƯỚC 1] Bộ tài liệu thiết kế phần mềm chuẩn SDLC
    │   ├── 01_SRS_Specification.md             # 1. Tài liệu Đặc tả Yêu cầu & Use Case
    │   ├── 02_System_And_Database_Design.md    # 2. Tài liệu Thiết kế Hệ thống & Database (ERD)
    │   ├── 03_Folder_And_Code_Architecture.md  # 3. Tài liệu Cấu trúc Folder & Quy chuẩn Code
    │   └── 04_API_Specification.md             # 4. Tài liệu Đặc tả chi tiết RESTful API
    │
    ├── migrations/                             # Các file quản lý phiên bản Database (SQL Migrations)
    │   ├── 000001_init_warehouse_schema.up.sql
    │   ├── 000001_init_warehouse_schema.down.sql
    │   ├── 000002_init_order_shipment_schema.up.sql
    │   ├── 000002_init_order_shipment_schema.down.sql
    │   ├── 000003_seed_master_statuses.up.sql
    │   └── 000003_seed_master_statuses.down.sql
    │
    ├── cmd/                                    # Điểm khởi chạy ứng dụng (Application Entrypoints)
    │   └── api/
    │       └── main.go                         # Khởi tạo Config, DB Pool, Dependency Injection, HTTP Server
    │
    ├── config/                                 # Quản lý cấu hình hệ thống
    │   └── config.go                           # Load cấu hình từ .env (DB_DSN, HTTP_PORT, JWT_SECRET)
    │
    ├── internal/                               # Mã nguồn nghiệp vụ nội bộ (Private Application Code)
    │   │
    │   ├── domain/                             # [TẦNG 1 - CORE] Entities, Value Objects & Interfaces
    │   │   ├── order.go                        # Entity: Order, OrderItem, OrderStatus, OrderStatusHistory
    │   │   ├── payment.go                      # Entity: Payment, Refund
    │   │   ├── shipment.go                     # Entity: Shipment, ShipmentItem, ShipmentStatus, ShipmentTrackingEvent
    │   │   ├── product.go                      # Entity: Product
    │   │   ├── warehouse.go                    # Entity: Warehouse, WarehouseStaff, Inventory, InventoryTransaction
    │   │   └── repository.go                   # Định nghĩa các Interfaces cho Repository & UnitOfWork (Transaction)
    │   │
    │   ├── dto/                                # Data Transfer Objects (Request / Response Structs & Validation tags)
    │   │   ├── order_dto.go                    # CreateOrderReq, UpdateOrderStatusReq, CancelOrderReq, OrderResp...
    │   │   ├── shipment_dto.go                 # CreateShipmentReq, ScanTrackingEventReq, ShipmentResp...
    │   │   ├── product_dto.go                  # CreateProductReq, UpdateProductReq, ProductFilter...
    │   │   └── warehouse_dto.go                # ImportStockReq, ExportStockReq, AdjustStockReq, InventoryTxFilter...
    │   │
    │   ├── repository/                         # [TẦNG 2 - DATA ACCESS] Triển khai truy vấn PostgreSQL
    │   │   └── postgres/
    │   │       ├── tx_manager.go               # Quản lý Database Transaction (WithTransaction(ctx, fn))
    │   │       ├── order_repository.go         # CRUD & Query cho orders, order_items, order_status_history
    │   │       ├── payment_repository.go       # CRUD & Query cho payments, refunds
    │   │       ├── shipment_repository.go      # CRUD & Query cho shipments, shipment_items, shipment_tracking_events
    │   │       ├── product_repository.go       # CRUD & Query cho products
    │   │       └── warehouse_repository.go     # CRUD & Query cho warehouses, warehouse_staff, inventory, inventory_transactions
    │   │
    │   ├── usecase/                            # [TẦNG 3 - BUSINESS LOGIC] Triển khai luồng nghiệp vụ của 2 UC
    │   │   ├── order_usecase.go                # Logic UC01: Tạo đơn, giữ tồn kho, đổi trạng thái, hủy đơn & hoàn tiền
    │   │   ├── shipment_usecase.go             # Logic UC01: Tạo vận đơn, xuất kho, quét mã hành trình giao hàng
    │   │   ├── product_usecase.go              # Logic UC02: Quản lý danh mục hàng hóa/sản phẩm
    │   │   └── warehouse_usecase.go            # Logic UC02: Quản lý kho, tồn kho, nhập/xuất/kiểm kê & lịch sử ra/vào kho
    │   │
    │   └── delivery/                           # [TẦNG 4 - PRESENTATION] Giao tiếp với bên ngoài
    │       └── http/
    │           ├── router.go                   # Đăng ký tất cả các nhóm route (/api/v1/orders, /api/v1/warehouses...)
    │           ├── middleware/
    │           │   ├── auth_middleware.go      # Xác thực JWT Token & Kiểm tra Role (CUSTOMER, STAFF, ADMIN)
    │           │   ├── cors_middleware.go      # Cấu hình CORS cho Frontend gọi API
    │           │   └── recovery_logger.go      # Ghi log request & chống crash server (Panic Recovery)
    │           └── handler/
    │               ├── order_handler.go        # HTTP Handlers cho Đơn hàng, Thanh toán, Hoàn tiền
    │               ├── shipment_handler.go     # HTTP Handlers cho Vận đơn & Lịch sử giao hàng
    │               ├── product_handler.go      # HTTP Handlers cho Sản phẩm
    │               └── warehouse_handler.go    # HTTP Handlers cho Kho, Hàng tồn kho & Lịch sử ra/vào kho
    │
    └── pkg/                                    # Các gói thư viện tiện ích dùng chung (Shared Packages)
        ├── apperror/
        │   └── apperror.go                     # Định nghĩa cấu trúc lỗi chuẩn (Code, HTTPStatus, Message)
        ├── response/
        │   └── response.go                     # Hàm helper trả về JSON thống nhất (Success, Error, Paginated)
        └── codegen/
            └── codegen.go                      # Sinh mã tự động: OrderCode (ORD-...), TrackingCode (TRK-...)
```

---

## 3. Phân Định Trách Nhiệm Từng Tầng (Layer Responsibilities)

| Tầng (Layer) | Đường dẫn | Trách nhiệm chính | Những điều CẤM (Anti-patterns) |
| :--- | :--- | :--- | :--- |
| **1. Domain** | `internal/domain/` | Định nghĩa các Struct ánh xạ thực thể nghiệp vụ (15 bảng ERD), hằng số trạng thái (`StatusPending`, `TxTypeImport`...) và các Interface. | Không import framework HTTP (`gin`/`fiber`) hoặc chi tiết kết nối DB vào tầng này. |
| **2. DTO** | `internal/dto/` | Định nghĩa cấu trúc JSON nhận từ Client (`Request`) kèm tag kiểm tra dữ liệu (`binding:"required,gt=0"`) và cấu trúc JSON trả về (`Response`). | Không viết logic tính toán nghiệp vụ hoặc câu lệnh SQL trong DTO. |
| **3. Repository** | `internal/repository/postgres/` | Thực thi các câu lệnh SQL với PostgreSQL, xử lý khóa dòng (`FOR UPDATE`) và quản lý Transaction (`BEGIN` / `COMMIT` / `ROLLBACK`). | Không chứa quy tắc rẽ nhánh nghiệp vụ (ví dụ: không quyết định khi nào tạo hoàn tiền ở tầng Repo). |
| **4. Usecase** | `internal/usecase/` | Trái tim của ứng dụng: Điều phối các Repository trong một Transaction để hoàn thành trọn vẹn một Use Case (VD: tạo đơn + trừ tồn giữ chỗ + tạo thanh toán). | Không phụ thuộc vào `http.Request` hay `http.ResponseWriter` (chỉ nhận `context.Context` và `dto`). |
| **5. Delivery (HTTP)** | `internal/delivery/http/` | Parse JSON/Query/Path params, lấy `user_id` từ JWT Context, gọi `Usecase`, và chuyển kết quả/lỗi thành HTTP Status Code chuẩn. | Không gọi trực tiếp `Repository` bỏ qua `Usecase`; không viết SQL trong Handler. |

---

## 4. Quy Chuẩn Kỹ Thuật Khi Triển Khai Code (Coding Standards)

### 4.1. Mẫu quản lý Database Transaction (Unit of Work Pattern)
Vì cả 2 UC có nhiều thao tác cập nhật đồng thời giữa `orders`, `shipments`, `inventory`, và `inventory_transactions`, tầng Repository cung cấp hàm `WithTransaction`:

```go
// Mẫu thiết kế Transaction Manager trong internal/repository/postgres/tx_manager.go
type TxManager interface {
    WithTransaction(ctx context.Context, fn func(txCtx context.Context) error) error
}
```
Khi `OrderUsecase.CreateOrder` hoặc `ShipmentUsecase.CreateShipment` chạy bên trong `WithTransaction`, tất cả các lời gọi `orderRepo`, `warehouseRepo`, `paymentRepo` sẽ tự động dùng chung 1 `sql.Tx` lấy từ `txCtx`. Nếu bất kỳ bước nào lỗi, toàn bộ sẽ `ROLLBACK`.

### 4.2. Quy chuẩn đặt tên (Naming Conventions)
* **Tên file:** Dùng `snake_case.go` (VD: `order_usecase.go`, `warehouse_repository.go`).
* **Tên biến / hàm trong Go:** Dùng `camelCase` cho private và `PascalCase` cho exported theo chuẩn Effective Go.
* **Tên trường JSON & Cột Database:** Dùng `snake_case` khớp 100% với sơ đồ ERD (VD: `order_id`, `reserved_quantity`, `from_warehouse_id`).
