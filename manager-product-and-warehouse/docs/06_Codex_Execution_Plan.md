# Kế Hoạch Thực Thi Codex Theo Từng Phần & Ranh Giới (Codex Execution Plan)

- **Model thực thi:** `gpt-6.2-sol` (`model_reasoning_effort = "medium"`)
- **Skills bắt buộc:** `.agents/skills/test-driven-development`, `.agents/skills/verification-before-completion`, `.agents/skills/executing-plans`
- **Phạm vi tuyệt đối:** Chỉ code **Backend Go + PostgreSQL (Docker Desktop)** trong `manager-product-and-warehouse/`. **KHÔNG code UI. KHÔNG tự build Docker image** cho đến khi toàn bộ Unit Test PASS và báo người dùng.

---

## Bảng Phân Chia 5 Phần & Ranh Giới Thực Thi

| Phần | Phạm vi & Files được phép tạo/sửa | Ranh giới CẤM chạm tới ở phần này | Tiêu chí PASS Unit Test trước khi sang phần tiếp |
| :--- | :--- | :--- | :--- |
| **Part 1**<br/>Scaffold, Config, Shared Pkgs, SQL Migrations & Docker Desktop | `go.mod`, `go.sum`, `.env.example`, `Dockerfile`, `docker-compose.yml`, `Makefile`, `config/config.go`, `pkg/apperror/*`, `pkg/response/*`, `pkg/codegen/*`, `migrations/000001..000003*.sql` | Không code `internal/domain`, `repository`, `usecase`, `delivery`, `cmd/`. Không build docker image. | `go test ./pkg/... ./config/... -v` PASS 100%. Kiểm tra SQL không chứa `FOREIGN KEY` / `REFERENCES` / `CHECK`. |
| **Part 2**<br/>Domain Entities, Repository Interfaces & `TxManager` | `internal/domain/*.go`, `internal/repository/postgres/tx_manager.go`, `internal/repository/postgres/tx_manager_test.go` | Không sửa `migrations/`, không code business logic ở `usecase/` hay HTTP ở `delivery/`. | `go test ./internal/domain/... ./internal/repository/postgres/... -v` PASS 100%. |
| **Part 3**<br/>UC02: Product, Warehouse, Inventory & Transactions | `internal/dto/product_dto.go`, `internal/dto/warehouse_dto.go`, `internal/repository/postgres/product_repository.go`, `warehouse_repository.go`, `internal/usecase/product_usecase.go`, `warehouse_usecase.go`, `product_usecase_test.go`, `warehouse_usecase_test.go` | Không code `order_*`, `payment_*`, `shipment_*`, không code `delivery/http/`. | `go test ./internal/usecase/... -v` PASS 100% (bao gồm test `ERR_INSUFFICIENT_STOCK`, `ERR_BELOW_RESERVED_STOCK`, `ERR_WAREHOUSE_ACCESS_DENIED`). |
| **Part 4**<br/>UC01: Order, Payment, Refund & Shipment | `internal/dto/order_dto.go`, `shipment_dto.go`, `internal/repository/postgres/order_repository.go`, `payment_repository.go`, `shipment_repository.go`, `internal/usecase/order_usecase.go`, `shipment_usecase.go`, `order_usecase_test.go`, `shipment_usecase_test.go` | Không code `delivery/http/` hay `cmd/api/main.go`. | `go test ./internal/usecase/... -v` PASS 100% (bao gồm test State Machine, Cancel+Refund, chống hủy 2 lần, Shipment 1:1, DELIVERED COD PAID, RETURNED hoàn kho). |
| **Part 5**<br/>HTTP Delivery, JWT/RBAC Middleware, Router & `cmd/api/main.go` | `internal/delivery/http/middleware/*.go`, `internal/delivery/http/handler/*.go`, `internal/delivery/http/router.go`, `cmd/api/main.go` + unit tests (`auth_middleware_test.go`, `handler/*_test.go`) | Không tạo UI. Không chạy `docker build`. | `go test ./... -v` PASS 100% và `go build ./cmd/api` thành công không lỗi. |

---

## Cấu Hình Triển Khai DB Bằng Docker Desktop (`docker-compose.yml`)
- Service `postgres`: image `postgres:16-alpine`, database `dc_post`, port `5432:5432`, mount volume lưu dữ liệu và mount `./migrations` vào `/docker-entrypoint-initdb.d` (chỉ các file `.up.sql` hoặc qua service `migrate` dùng `migrate/migrate`) kèm `healthcheck` (`pg_isready`).
- Service `api`: khai báo `build: .` với `Dockerfile` multi-stage Go 1.24 + alpine, `depends_on` postgres `service_healthy` — sẵn sàng để người dùng chạy `docker compose up --build` sau khi toàn bộ unit test PASS.
