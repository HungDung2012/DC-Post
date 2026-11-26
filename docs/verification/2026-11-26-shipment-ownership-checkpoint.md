# Checkpoint Task 14 — Customer shipment API và quyền sở hữu

Ngày 26/11/2026, nhánh feat/customer-service, tiếp nối Task 13. Chạy trực tiếp
JDK 21/MySQL 8.4 cục bộ; không Docker. Không thêm bảng hoặc migration.

## Phạm vi đã triển khai

- POST /api/v1/customer/shipments dùng JWT principal và Idempotency-Key, gọi lại
  dịch vụ tạo ONLINE đã có. Không nhận customer/actor/channel từ payload. CASH=PENDING,
  VNPAY=WAITING_PAYMENT; không gọi Shipping khi tạo.
- GET /api/v1/customer/shipments giới hạn ownership trong SQL, áp dụng cả content,
  search và count. Tìm tracking/recipient/phone với wildcard được escape; lọc local
  paymentMethod, syncStatus, ngày tạo. Page 0–1.000.000, size 1–100, q tối đa 500 ký tự.
  Sort cố định createdAt DESC, shipmentId DESC; từ chối override sort.
- GET /api/v1/customer/shipments/{id} query bằng id AND customerId; người khác và
  ID không tồn tại cùng trả 404 SHIPMENT_NOT_FOUND. DTO snapshot/items ánh xạ trong
  transaction đọc ngắn; không trả entity, hash/key hoặc scheduler internals.
- Chỉ detail đã SYNCED mới lấy tracking qua ShippingClient, sau khi transaction đọc
  kết thúc. Dependency lỗi hoặc tracking sai cấu trúc chỉ làm tracking UNAVAILABLE;
  snapshot vẫn được trả. Không dùng privateDetails thay snapshot hoặc lộ trong response.
- Capabilities công khai paymentMethodFilter=true, paymentStatusFilter=false,
  shippingStatusFilter=false. Bộ lọc không hỗ trợ trả 400 FILTER_UNAVAILABLE. Payment
  chưa có schema/dữ liệu tại Task 14; hoàn thiện bộ lọc local sau Tasks 19–24, không suy
  ra trạng thái tài chính từ syncStatus. Shipping chưa có batch/filter capability.
- Không thêm PUT/PATCH/DELETE sửa vận đơn, không định nghĩa contract Go chính thức.
  ID bưu cục/actor giữ tạm INT theo quyết định trước.

Tài liệu API: [customer-shipment-api.md](../references/customer-shipment-api.md).

## TDD và review

ShipmentOwnershipIT: 13 assertion failures RED trước khi controller tồn tại; GREEN
sau khi triển khai. Kiểm tra hai customer, count/search/ownership, sort thời gian
trùng, paging, lọc trước paging, snapshot/items, không override customer, metadata
tạo ONLINE và enrichment ngoài transaction khi Shipping thành công/thất bại.

Code review phát hiện hai Important: thiếu kiểm tra Instant ngoài miền MySQL
DATETIME và thiếu kiểm tra timestamp/event của tracking. ShipmentQueryServiceTest
có RED 3 tests, 2 assertion failures, 0 errors trước bản sửa; GREEN 3/0/0 sau sửa.
Ngày ngoài khoảng UTC 1000-01-01 đến 9999-12-31T23:59:59.999999 bị từ chối trước
reader. Tracking null/sai mã/thiếu trạng thái, timestamp, danh sách hoặc event chuyển
EXTERNAL_INVALID_RESPONSE và vẫn giữ snapshot. Review lại không còn Critical/Important.

## Verification

| Kiểm tra | Kết quả |
|---|---|
| `mvn -B -ntp -f customer-service/pom.xml install` | BUILD SUCCESS, artifact cập nhật trước test module |
| `mvn -B -ntp -f test/backend/pom.xml clean test -Dtest=ShipmentQueryServiceTest` | 3 pass |
| `mvn -B -ntp -f test/backend/pom.xml verify -Pintegration` | 85 unit/HTTP + 67 MySQL IT = 152; 0 failures/errors/skips |
| ShipmentOwnershipIT trong full verify | 13 pass qua controller/filters và MySQL thật |
| `python test/tools/verify_packaging.py` | Hai JAR đúng Java 21/layout; không test artifacts |
| Backend chạy JAR mới bằng Java | Flyway validate 2 migrations, schema hiện hành, startup thành công |
| GET http://localhost:18080/api/v1/public/health | HTTP 200, status UP |
| GET /api/v1/customer/shipments không JWT | HTTP 401 |

MySQL customer_test tách khỏi customer_dev. TEST_DB_* lấy cấu hình cục bộ đã có,
không in/commit secret hoặc thay quyền tài khoản. Build/test tuần tự; heap mỗi JVM
256 MB, 2 CPU. Tất cả test/mocks nằm trong test/, không đưa vào ứng dụng.

Log cục bộ không commit: .cache/task14-ownership-red2.log,
.cache/task14-ownership-green1.log, .cache/task14-query-red.log,
.cache/task14-query-green.log, .cache/task14-install.log,
.cache/task14-full-verify.log, .cache/task14-development-startup.log.

Bước tiếp theo: Task 15 — scheduler gửi đơn đủ điều kiện và atomic claim.
