# Manager Product & Warehouse Microservice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hoàn chỉnh Microservice `manager-product-and-warehouse` bằng Golang và PostgreSQL phục vụ 2 đại Use Case (**UC01: Quản lý đơn hàng** và **UC02: Quản lý kho**) cho hệ thống quản lý vận đơn DC-Post.

**Architecture:** Clean Architecture phân 4 tầng (`domain`, `repository/postgres`, `usecase`, `delivery/http`) gói gọn trong thư mục `manager-product-and-warehouse`. Toàn bộ nghiệp vụ đa bảng (đặt đơn + giữ kho + thanh toán; tạo vận đơn + xuất kho; hủy đơn + nhả kho + hoàn tiền) chạy trong 1 PostgreSQL ACID Transaction thông qua `TxManager`, kết hợp khóa dòng `SELECT ... FOR UPDATE` chống âm kho.

**Tech Stack:** Go 1.24+, Gin (`github.com/gin-gonic/gin`), PostgreSQL (`github.com/jackc/pgx/v5`), JWT (`github.com/golang-jwt/jwt/v5`), `golang-migrate`.

**Spec:**
- [01_SRS_Specification.md](./01_SRS_Specification.md)
- [02_System_And_Database_Design.md](./02_System_And_Database_Design.md)
- [03_Folder_And_Code_Architecture.md](./03_Folder_And_Code_Architecture.md)
- [04_API_Specification.md](./04_API_Specification.md)

---

## Global Constraints

- Toàn bộ source code, migration, config và docs nằm hoàn toàn trong thư mục `manager-product-and-warehouse/`.
- **Shared PostgreSQL Database (`dc_post`) — KHÔNG tạo Foreign Key Constraint hoặc Check Constraint giữa các bảng trong SQL** (chỉ dùng `PRIMARY KEY`, `UNIQUE` trên mã nghiệp vụ & `(warehouse_id, product_id)`, và `NOT NULL`); tính toàn vẹn tham chiếu và quy tắc nghiệp vụ kiểm tra 100% ở tầng Go Usecase.
- Bảng `orders` có thêm cột `warehouse_id INTEGER NOT NULL` để lưu kho xử lý đơn ngay lúc tạo đơn.
- Cột thời gian giao hàng trong `shipments` chuẩn hóa tên `delivered_at TIMESTAMPTZ NULL`; `order_status_history.change_at` dùng `TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- Phân quyền chuẩn 3 Role trong JWT HS256 (`user_id`, `email`, `role`): `CUSTOMER`, `STAFF`, `ADMIN`. Tài khoản `STAFF` chỉ thao tác được trên `warehouse_id` được gán trong `warehouse_staff`; `ADMIN` toàn quyền mọi kho.
- 1 Đơn hàng = 1 Vận đơn trọn gói (`POST /api/v1/shipments` tự động đóng gói toàn bộ `order_items`).
- Tất cả JSON field dùng `snake_case` và trả về theo cấu trúc response chuẩn `{ "success", "message", "data", "meta" }` hoặc `{ "success": false, "error": { "code", "message", "details" } }`.

---

## Review Focus

Năm nhóm tình huống biên / lỗi hệ thống dễ xảy ra nhất cần được khóa chặt bằng test ngay từ đầu:

1. **Đặt hàng đồng thời cùng 1 sản phẩm (Concurrent Overselling):** Hai request `POST /api/v1/orders` cùng đặt số lượng vượt tồn khả dụng (`quantity - reserved_quantity`) tại cùng 1 thời điểm -> bắt buộc khóa `SELECT ... FOR UPDATE` theo thứ tự `product_id` tăng dần (tránh deadlock), 1 request thành công, 1 request nhận `400 ERR_INSUFFICIENT_STOCK`.
2. **Hủy đơn hàng đã chuyển sang `SHIPPING` hoặc hủy trùng 2 lần:** Request `POST /api/v1/orders/:id/cancel` lần 2 hoặc trên đơn đang giao -> phải trả về `422 ERR_INVALID_STATUS_TRANSITION`, tuyệt đối không nhả `reserved_quantity` 2 lần hoặc tạo 2 bản ghi `refunds`.
3. **Kiểm kê tồn kho thấp hơn số lượng đang giữ chỗ (`actual_quantity < reserved_quantity`):** Request `POST /api/v1/inventory/adjust` với `actual_quantity` nhỏ hơn `reserved_quantity` -> phải từ chối với `422 ERR_BELOW_RESERVED_STOCK`, không được ghi đè làm âm tồn khả dụng.
4. **Chuyển trạng thái đơn hàng đi đường tắt qua `PATCH /api/v1/orders/:id/status`:** Gọi `PATCH` với `new_status_code = "SHIPPING" | "DELIVERED" | "RETURNED" | "CANCELLED"` -> phải từ chối với `422 ERR_INVALID_STATUS_TRANSITION` để không bỏ qua bước trừ kho / tạo vận đơn / hoàn tiền.
5. **Nhân viên `STAFF` thao tác trái kho được phân công:** `STAFF` không có bản ghi trong `warehouse_staff` cho `warehouse_id` mục tiêu gọi nhập/xuất/kiểm kê kho hoặc tạo vận đơn -> phải trả về `403 ERR_WAREHOUSE_ACCESS_DENIED`.

---

## Kế Hoạch Triển Khai Theo Từng Task (Bite-Sized TDD Tasks)

### Task 1: Khởi tạo Go Module, Cấu hình, Shared Packages & SQL Migrations (15 bảng không FK)

**Files:**
- Create: `manager-product-and-warehouse/go.mod`
- Create: `manager-product-and-warehouse/.env.example`
- Create: `manager-product-and-warehouse/docker-compose.yml`
- Create: `manager-product-and-warehouse/Makefile`
- Create: `manager-product-and-warehouse/config/config.go`
- Create: `manager-product-and-warehouse/pkg/apperror/apperror.go`
- Create: `manager-product-and-warehouse/pkg/response/response.go`
- Create: `manager-product-and-warehouse/pkg/codegen/codegen.go`
- Create: `manager-product-and-warehouse/migrations/000001_init_warehouse_schema.up.sql`
- Create: `manager-product-and-warehouse/migrations/000001_init_warehouse_schema.down.sql`
- Create: `manager-product-and-warehouse/migrations/000002_init_order_shipment_schema.up.sql`
- Create: `manager-product-and-warehouse/migrations/000002_init_order_shipment_schema.down.sql`
- Create: `manager-product-and-warehouse/migrations/000003_seed_master_statuses.up.sql`
- Create: `manager-product-and-warehouse/migrations/000003_seed_master_statuses.down.sql`
- Test: `manager-product-and-warehouse/pkg/codegen/codegen_test.go`

**Interfaces:**
- Produces:
  - `config.Load() (*config.Config, error)`
  - `apperror.New(httpStatus int, code, message string, details any) *apperror.AppError` và các biến lỗi chuẩn (`ErrInsufficientStock`, `ErrInvalidStatusTransition`, `ErrBelowReservedStock`, `ErrWarehouseAccessDenied`, `ErrResourceNotFound`, `ErrDuplicateCode`)
  - `response.JSONSuccess(c *gin.Context, status int, msg string, data any, meta *response.Meta)`
  - `response.JSONError(c *gin.Context, err error)`
  - `codegen.NewOrderCode(now time.Time) string` (định dạng `ORD-YYYYMMDD-XXXXXX`)
  - `codegen.NewTrackingCode(now time.Time) string` (định dạng `TRK-YYYYMMDD-XXXXXX`)

- [ ] **Step 1: Write failing tests for `codegen`**
  Viết `TestNewOrderCode_Format` và `TestNewTrackingCode_Format` kiểm tra prefix `ORD-`/`TRK-`, ngày `YYYYMMDD` và tính không trùng lặp khi sinh liên tiếp 100 mã.
- [ ] **Step 2: Run test to verify it fails**
  Run: `go test ./pkg/codegen/... -v`
  Expected: FAIL (`undefined: NewOrderCode`)
- [ ] **Step 3: Implement `config`, `pkg/apperror`, `pkg/response`, `pkg/codegen` và 3 cặp file SQL Migrations**
  Đảm bảo 15 bảng trong `000001` & `000002` không chứa bất kỳ `REFERENCES` / `FOREIGN KEY` / `CHECK` constraint nào; `000003` seed 7 `order_statuses` (`PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPING`, `DELIVERED`, `CANCELLED`, `RETURNED`) và 5 `shipment_statuses` (`CREATED`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `RETURNED`).
- [ ] **Step 4: Verify tests & SQL migration syntax**
  Run: `go test ./pkg/... -v`
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "chore: init microservice scaffold, shared packages, and no-fk postgres migrations"`

---

### Task 2: Tầng Domain Entities, Interfaces & PostgreSQL Transaction Manager (`TxManager`)

**Files:**
- Create: `manager-product-and-warehouse/internal/domain/product.go`
- Create: `manager-product-and-warehouse/internal/domain/warehouse.go`
- Create: `manager-product-and-warehouse/internal/domain/order.go`
- Create: `manager-product-and-warehouse/internal/domain/payment.go`
- Create: `manager-product-and-warehouse/internal/domain/shipment.go`
- Create: `manager-product-and-warehouse/internal/domain/repository.go`
- Create: `manager-product-and-warehouse/internal/repository/postgres/tx_manager.go`
- Test: `manager-product-and-warehouse/internal/repository/postgres/tx_manager_test.go`

**Interfaces:**
- Produces:
  - Toàn bộ struct domain ánh xạ 15 bảng ERD (`Product`, `Warehouse`, `WarehouseStaff`, `Inventory`, `InventoryTransaction`, `Order`, `OrderItem`, `OrderStatus`, `OrderStatusHistory`, `Payment`, `Refund`, `Shipment`, `ShipmentItem`, `ShipmentStatus`, `ShipmentTrackingEvent`) cùng hằng số status/transaction types.
  - `domain.TxManager` (`WithTransaction(ctx context.Context, fn func(txCtx context.Context) error) error`)
  - `postgres.GetQuerier(ctx context.Context, pool *pgxpool.Pool) postgres.DBTX` (lấy `pgx.Tx` từ `ctx` nếu đang trong transaction, ngược lại trả về `pool`).
  - Interfaces: `ProductRepository`, `WarehouseRepository`, `OrderRepository`, `PaymentRepository`, `ShipmentRepository`.

- [ ] **Step 1: Write failing test for `TxManager` context propagation**
  Viết `TestGetQuerier_ReturnsTxWhenPresent` kiểm tra `GetQuerier` trả về `pgx.Tx` khi có trong context và trả về fallback querier khi không có.
- [ ] **Step 2: Run test to verify it fails**
  Run: `go test ./internal/repository/postgres/... -v`
  Expected: FAIL
- [ ] **Step 3: Implement Domain structs, Repository interfaces, và `tx_manager.go`**
- [ ] **Step 4: Run test to verify it passes**
  Run: `go test ./internal/domain/... ./internal/repository/postgres/... -v`
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat: define domain entities, repository interfaces, and pgx tx manager"`

---

### Task 3: UC02 — Quản Lý Sản Phẩm & Kho (`ProductRepository`, `WarehouseRepository`, `ProductUsecase`, `WarehouseUsecase`)

**Files:**
- Create: `manager-product-and-warehouse/internal/dto/product_dto.go`
- Create: `manager-product-and-warehouse/internal/dto/warehouse_dto.go`
- Create: `manager-product-and-warehouse/internal/repository/postgres/product_repository.go`
- Create: `manager-product-and-warehouse/internal/repository/postgres/warehouse_repository.go`
- Create: `manager-product-and-warehouse/internal/usecase/product_usecase.go`
- Create: `manager-product-and-warehouse/internal/usecase/warehouse_usecase.go`
- Test: `manager-product-and-warehouse/internal/usecase/warehouse_usecase_test.go`

**Interfaces:**
- Consumes: `domain.TxManager`, `domain.ProductRepository`, `domain.WarehouseRepository`, `apperror`
- Produces:
  - `ProductUsecase`: `Create`, `List`, `GetByID`, `Update`
  - `WarehouseUsecase`: `CreateWarehouse`, `ListWarehouses`, `UpdateWarehouse`, `AssignStaff`, `RemoveStaff`, `GetWarehouseInventory`, `ImportStock`, `ExportManualStock`, `AdjustStock`, `ListInventoryTransactions`
  - `WarehouseRepository.LockInventoryForUpdate(ctx, warehouseID int, productIDs []int) (map[int]*domain.Inventory, error)` (sort `productIDs` tăng dần trước khi `SELECT ... FOR UPDATE` để tránh deadlock).

- [ ] **Step 1: Write failing unit tests for `WarehouseUsecase` (Review Focus #3 & #5)**
  Viết tests với mock repositories:
  - `TestImportStock_CreatesOrIncrementsInventoryAndLogsTransaction`
  - `TestExportManualStock_FailsWhenExceedsAvailableStock` (trả `ERR_INSUFFICIENT_STOCK`)
  - `TestAdjustStock_FailsWhenBelowReservedQuantity` (trả `ERR_BELOW_RESERVED_STOCK`)
  - `TestStaffWarehouseAccess_DeniedWhenNotAssigned` (trả `ERR_WAREHOUSE_ACCESS_DENIED` khi `role == "STAFF"` không có trong `warehouse_staff`; `ADMIN` được phép).
- [ ] **Step 2: Run tests to verify they fail**
  Run: `go test ./internal/usecase/... -v`
  Expected: FAIL
- [ ] **Step 3: Implement DTOs, PostgreSQL repositories (`product_repository.go`, `warehouse_repository.go`), và Usecases (`product_usecase.go`, `warehouse_usecase.go`)**
- [ ] **Step 4: Run tests to verify they pass**
  Run: `go test ./internal/usecase/... -v`
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(warehouse): implement product, warehouse, inventory and transaction usecases"`

---

### Task 4: UC01 (Phần 1) — Quản Lý Đơn Hàng, Thanh Toán, Hủy Đơn & Hoàn Tiền (`OrderUsecase`)

**Files:**
- Create: `manager-product-and-warehouse/internal/dto/order_dto.go`
- Create: `manager-product-and-warehouse/internal/repository/postgres/order_repository.go`
- Create: `manager-product-and-warehouse/internal/repository/postgres/payment_repository.go`
- Create: `manager-product-and-warehouse/internal/usecase/order_usecase.go`
- Test: `manager-product-and-warehouse/internal/usecase/order_usecase_test.go`

**Interfaces:**
- Consumes: `domain.TxManager`, `domain.OrderRepository`, `domain.PaymentRepository`, `domain.WarehouseRepository`, `domain.ProductRepository`, `codegen`
- Produces:
  - `OrderUsecase`: `CreateOrder`, `ListOrders`, `GetOrderDetail`, `UpdateOrderStatus`, `GetOrderStatusHistory`, `CancelOrder`, `ConfirmPayment`, `UpdateRefundStatus`

- [ ] **Step 1: Write failing unit tests for `OrderUsecase` (Review Focus #1, #2, #4)**
  Viết tests:
  - `TestCreateOrder_ReservesStockAndCreatesPaymentAndHistory` (lưu `orders.warehouse_id`, tăng `reserved_quantity`, ghi `inventory_transactions` `RESERVE`, ghi `order_status_history`, tạo `payments` `PENDING`)
  - `TestCreateOrder_FailsOnInsufficientAvailableStock` (trả `ERR_INSUFFICIENT_STOCK`)
  - `TestUpdateOrderStatus_RejectsShippingDeliveredReturnedCancelled` (trả `ERR_INVALID_STATUS_TRANSITION`)
  - `TestCancelOrder_ReleasesReservedStockAndCreatesRefundWhenPaid` (nhả `reserved_quantity` tại `order.warehouse_id`, ghi `RELEASE`, tạo `refunds` khi `payment_status == "PAID"`)
  - `TestCancelOrder_RejectsWhenAlreadyShippingOrCancelled` (trả `ERR_INVALID_STATUS_TRANSITION`, không nhả kho lần 2).
- [ ] **Step 2: Run tests to verify they fail**
  Run: `go test ./internal/usecase -run Test.*Order -v`
  Expected: FAIL
- [ ] **Step 3: Implement `order_dto.go`, `order_repository.go`, `payment_repository.go`, `order_usecase.go`**
- [ ] **Step 4: Run tests to verify they pass**
  Run: `go test ./internal/usecase/... -v`
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(order): implement order creation, status machine, cancellation, payment and refund"`

---

### Task 5: UC01 (Phần 2) — Quản Lý Vận Đơn, Xuất Kho & Hành Trình Giao Hàng (`ShipmentUsecase`)

**Files:**
- Create: `manager-product-and-warehouse/internal/dto/shipment_dto.go`
- Create: `manager-product-and-warehouse/internal/repository/postgres/shipment_repository.go`
- Create: `manager-product-and-warehouse/internal/usecase/shipment_usecase.go`
- Test: `manager-product-and-warehouse/internal/usecase/shipment_usecase_test.go`

**Interfaces:**
- Consumes: `domain.TxManager`, `domain.ShipmentRepository`, `domain.OrderRepository`, `domain.PaymentRepository`, `domain.WarehouseRepository`, `codegen`
- Produces:
  - `ShipmentUsecase`: `CreateShipment`, `ScanTrackingEvent`, `GetShipmentByTrackingCode`

- [ ] **Step 1: Write failing unit tests for `ShipmentUsecase`**
  Viết tests:
  - `TestCreateShipment_PacksAllOrderItemsDeductsStockAndTransitionsToShipping` (1 Order = 1 Shipment trọn gói, trừ `quantity` & `reserved_quantity` tại `order.warehouse_id`, ghi `EXPORT` có `shipment_id`, chuyển `orders` sang `SHIPPING`, tính `cod_amount`)
  - `TestScanTrackingEvent_DeliveredMarksOrderDeliveredAndCODPaid` (cập nhật `delivered_at`, đổi `orders` sang `DELIVERED`, đổi payment COD sang `PAID`)
  - `TestScanTrackingEvent_ReturnedRestoresStockAtScanWarehouse` (đổi `orders` sang `RETURNED`, cộng `quantity` tại `warehouse_id` quét hoàn, ghi `RETURN`).
- [ ] **Step 2: Run tests to verify they fail**
  Run: `go test ./internal/usecase -run Test.*Shipment -v`
  Expected: FAIL
- [ ] **Step 3: Implement `shipment_dto.go`, `shipment_repository.go`, `shipment_usecase.go`**
- [ ] **Step 4: Run tests to verify they pass**
  Run: `go test ./internal/usecase/... -v`
  Expected: PASS
- [ ] **Step 5: Commit**
  `git commit -m "feat(shipment): implement shipment dispatch, tracking events, and order/inventory sync"`

---

### Task 6: Tầng HTTP Delivery, JWT Auth Middleware, Router & Entrypoint (`cmd/api/main.go`)

**Files:**
- Create: `manager-product-and-warehouse/internal/delivery/http/middleware/auth_middleware.go`
- Create: `manager-product-and-warehouse/internal/delivery/http/middleware/cors_middleware.go`
- Create: `manager-product-and-warehouse/internal/delivery/http/handler/product_handler.go`
- Create: `manager-product-and-warehouse/internal/delivery/http/handler/warehouse_handler.go`
- Create: `manager-product-and-warehouse/internal/delivery/http/handler/order_handler.go`
- Create: `manager-product-and-warehouse/internal/delivery/http/handler/shipment_handler.go`
- Create: `manager-product-and-warehouse/internal/delivery/http/router.go`
- Create: `manager-product-and-warehouse/cmd/api/main.go`
- Test: `manager-product-and-warehouse/internal/delivery/http/middleware/auth_middleware_test.go`

**Interfaces:**
- Consumes: Tất cả Usecases từ Task 3, 4, 5; `config.Load`, `response`, `apperror`
- Produces:
  - HTTP Server lắng nghe trên `HTTP_PORT` (mặc định `:8082`) phục vụ toàn bộ endpoints trong `04_API_Specification.md`.
  - `middleware.JWTAuth(secret string) gin.HandlerFunc` & `middleware.RequireRoles(roles ...string) gin.HandlerFunc`.

- [ ] **Step 1: Write failing tests for JWT Auth & RBAC Middleware**
  Viết tests kiểm tra: thiếu token -> `401`, token hợp lệ nhưng `role = "CUSTOMER"` gọi route chỉ dành cho `STAFF`/`ADMIN` -> `403`, token hợp lệ với đúng role -> gắn `user_id` & `role` vào `gin.Context` và đi tiếp.
- [ ] **Step 2: Run tests to verify they fail**
  Run: `go test ./internal/delivery/http/middleware/... -v`
  Expected: FAIL
- [ ] **Step 3: Implement Middlewares, 4 Handlers, `router.go` và `cmd/api/main.go`**
- [ ] **Step 4: Run full test suite & verify binary builds**
  Run: `go test ./... -v && go build ./cmd/api`
  Expected: Tất cả tests PASS và build thành công không lỗi.
- [ ] **Step 5: Commit**
  `git commit -m "feat(http): wire gin router, jwt auth middleware, handlers and server entrypoint"`
