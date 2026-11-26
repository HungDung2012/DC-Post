# Checkpoint Task 15 — Scheduler và atomic claim

Ngày 26/11/2026, nhánh feat/customer-service, tiếp nối Task 14. Chạy JDK 21/MySQL
8.4 cục bộ, không Docker. Không thêm dependency, bảng hoặc migration.

## Phạm vi

- ShipmentSchedulingConfiguration bật lịch chạy theo cấu hình; ShipmentSyncScheduler
  xử lý từng lô tuần tự, mặc định 10 ID, nghỉ 10 giây sau mỗi lô. Giới hạn lô 1–100.
  Tắt tự động bằng SHIPMENT_SYNC_ENABLED=false; test profile tắt lịch nền.
- Candidate query giới hạn CASH/PENDING, chưa hủy, đến hạn, có thứ tự và LIMIT.
  UPDATE claim lặp lại các điều kiện dưới khóa hàng; đặt PROCESSING/start time/tăng
  attempt cùng transaction. Đọc snapshot/items rồi commit trước ShippingClient.submit.
- Orchestration và scheduler dùng NEVER; state service dùng REQUIRES_NEW. Không giữ
  transaction/khóa khi HTTP và không đặt transaction quanh cả lô.
- Payload gửi từ snapshot đã lưu; chuyển warehouse INT sang chuỗi tại biên adapter
  hiện có. Khóa gửi ổn định shipment:<trackingCode>. Dùng cùng luồng cho ONLINE và
  Guest COUNTER, không cần liên kết tài khoản Guest.
- Completion ghi bằng UPDATE chỉ khi PROCESSING/attempt/startedAt còn đúng. Worker
  cũ và response trùng không ghi đè kết quả mới. CONFIRMED=SYNCED, REJECTED=BLOCKED,
  UNKNOWN/null/timeout/kết quả không chắc chắn=UNKNOWN; auth lỗi=BLOCKED. Chỉ lưu mã
  enum đã làm sạch, không lưu/log payload hoặc thông báo lỗi bên ngoài.
- Không tự retry UNKNOWN/BLOCKED. PROCESSING bị gián đoạn, đối soát và backoff thuộc
  Task 16. Chưa tuyên bố exactly-once xuyên service khi chưa có contract Shipping thật.

VNPAY hiện luôn bị chặn, kể cả bị đổi nhầm sang PENDING, vì payment schema/dữ liệu
xác minh còn ở Tasks 19–21. Plan Task 21 đã bổ sung bước bắt buộc nối PAID đã xác
minh vào candidate/claim trong cùng khóa shipment; callback đổi PENDING một mình
không đủ. Không thêm bảng sớm hoặc dùng syncStatus làm chứng cứ đã thanh toán.

Tài liệu vận hành và cấu hình: [shipment-synchronization.md](../references/shipment-synchronization.md).

## TDD và review

| Vòng kiểm thử | RED | GREEN |
|---|---|---|
| ShipmentSyncService gửi claim đã nhận | 1 assertion failure, 0 errors; chưa gọi Shipping | 1 pass |
| Claim/commit thật MySQL | 1 assertion failure, 0 errors; chưa có claim | 1 pass |
| Kết quả chưa rõ/lỗi dependency | 9 tests, 2 assertion failures, 0 errors | 9 pass |
| Scheduler/wiring | 4 tests, 2 assertion failures, 0 errors | 4 pass |
| Candidate query | 10 tests, 1 assertion failure, 0 errors | Trong full suite 14 IT pass |

Các guard và regression bổ sung đã pass: VNPAY WAITING_PAYMENT/PENDING không gửi;
các trạng thái hủy và đơn chưa đến hạn không gửi; hai worker dùng barrier/latch chỉ
một HTTP call; attempt/start/state cũ không ghi đè; caller transaction bị chặn;
null reply; Guest COUNTER CASH; thứ tự/giới hạn candidate. Không tạo RED giả cho guard
đã đúng sau implementation.

Ca loopback gọi ShippingHttpAdapter thật qua HTTP server JVM ở cổng ngẫu nhiên:
interceptor tại biên gọi adapter xác minh không có transaction; server đọc MySQL qua
connection khác thấy PROCESSING đã commit, nhận đúng snapshot/items/key, trả CONFIRMED
và kết quả local thành SYNCED. Không cần mock server bên ngoài hoặc Docker.

Review độc lập chỉ đọc source/tests: không có Critical/Important. Đã đối chiếu điều
kiện claim, transaction, stale worker, batch và giới hạn VNPAY. Chưa xem mock protocol
đã kiểm thử là hoàn tất tích hợp Go thật.

## Verification

| Kiểm tra | Kết quả |
|---|---|
| Build/install application sau thay đổi source cuối | BUILD SUCCESS, JDK 21 |
| `mvn -B -ntp -f test/backend/pom.xml verify -Pintegration` | 91 unit/HTTP + 81 MySQL IT = 172, 0 failures/errors/skips |
| ShipmentSyncServiceTest / ShipmentSyncSchedulerTest | 2 + 4 pass |
| ShipmentSyncClaimIT | 14 pass, gồm HTTP loopback thật |
| `python test/tools/verify_packaging.py` | Hai JAR Java 21 đúng layout, không test artifacts |
| Backend chạy trực tiếp JAR mới | Startup thành công; Flyway validate 2 migrations, schema hiện hành |
| GET http://localhost:18080/api/v1/public/health | HTTP 200, status UP |
| GET /api/v1/customer/shipments không JWT | HTTP 401 |

MySQL customer_test tách khỏi customer_dev. TEST_DB_* chỉ nạp cục bộ, không in hoặc
commit credential. Build/test tuần tự, heap mỗi JVM 256 MB và 2 CPU. Backend dùng
Serial GC. Các fixture, mocks, test config/script chỉ trong test/.

Log cục bộ không commit: .cache/task15-sync-red.log, .cache/task15-sync-green.log,
.cache/task15-claim-red.log, .cache/task15-claim-green.log, .cache/task15-guards.log,
.cache/task15-outcome-red.log, .cache/task15-outcome-green.log,
.cache/task15-scheduler-red.log, .cache/task15-candidates-red.log,
.cache/task15-scheduler-green.log, .cache/task15-install.log,
.cache/task15-full-verify.log, .cache/task15-development-startup.log.

Bước tiếp theo: Task 16 — timeout, restart, đối soát và retry an toàn.
