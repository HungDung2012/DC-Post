# Checkpoint Task 12 — Tạo vận đơn và idempotency

Ngày 24/11/2026, nhánh `feat/customer-service`. JDK 21, MySQL 8.4 cục bộ; không Docker.

## Phạm vi

- Tạo ONLINE HOME_PICKUP/DROP_OFF từ CustomerPrincipal, không nhận customer/actor ID từ payload.
- Chuẩn hóa contact, điện thoại, ghi chú và BigDecimal; validate độ dài, miền số và scale theo schema trước lưu để tránh MySQL tự làm tròn. Danh sách items được sao chép bất biến; giữ thứ tự khai báo.
- DROP_OFF kiểm tra ID INT tạm với danh sách Warehouse qua interface; không ép UUID/chuỗi không tương ứng thành số. Actor chuyển bằng Math.toIntExact, không cắt số. Kiểu thật vẫn chờ xác minh Task 32.
- Pricing xác minh lại phí VND nguyên, không âm, trong miền cột. Phí khác confirmedFee trả 409/FEE_CHANGED cùng currentFee để xác nhận lại, không lưu đơn. Lỗi dependency không có fallback phí giả.
- Scope `customer:<userId>` và key ASCII phân biệt hoa thường, dài 1–128. SHA-256 trên JSON record đã chuẩn hóa bằng mapper riêng, không timestamp. Mọi trường nghiệp vụ, kể cả phí đã xác nhận và thứ tự items, tham gia hash; null/ghi chú trắng và các cách biểu diễn decimal tương đương được chuẩn hóa.
- Replay kiểm tra ownership/hash trước gọi dependency; cùng key khác nội dung trả 409/IDEMPOTENCY_CONFLICT. Không trả JPA entity cho API.
- Orchestration dùng propagation NEVER, writer bean riêng giữ transaction ngắn. Sau lỗi unique MySQL 1062 ở writer đã rollback, đọc bản ghi thắng cho key conflict; tracking collision thử tối đa ba lần, mỗi lần writer có transaction mới. Lỗi constraint khác được truyền tiếp, không tự retry.
- Lưu shipment/items/snapshot/phí đã xác minh nguyên tử. CASH bắt đầu PENDING; VNPAY WAITING_PAYMENT. Không gọi Shipping khi tạo đơn, chưa xử lý thanh toán/scheduler.
- Không thêm bảng/cột/migration; V2 đã áp dụng được giữ nguyên checksum. Không định nghĩa contract Go chính thức.

## TDD và review

Đã quan sát assertion RED rồi GREEN cho tạo đơn/snapshot, validation/bưu cục/phí, replay/payload conflict/tracking retry, phản hồi Pricing không hợp lệ và HTTP Problem Details. Các log RED nằm ở `.cache/task12-unit-red*.log`, `.cache/task12-http-red.log`, không commit.

Kiểm thử MySQL chạy trên customer_test riêng. Ca rollback đưa quantity=0 vào writer để gây lỗi CHECK ở item thứ hai; MySQL/Hibernate ánh xạ lỗi CHECK thành JpaSystemException, khác lỗi duplicate DataIntegrityViolationException. Test kiểm tra đúng lớp lỗi và xác nhận không còn shipment/items sau rollback.

Review độc lập không phát hiện Critical/Important. Đề xuất Minor bổ sung race cùng key/khác payload đã thực hiện: một request thành công, request còn lại nhận IDEMPOTENCY_CONFLICT, chỉ một bộ items được lưu.

## Verification

| Kiểm tra | Kết quả |
|---|---|
| Build/install application, JDK 21 | BUILD SUCCESS |
| `mvn -B -ntp -f test/backend/pom.xml verify -Pintegration` | 82 unit/HTTP + 45 MySQL IT = 127; 0 failure/error/skip |
| ShipmentCreationServiceTest | 14 pass |
| ShipmentCreationHttpTest | 4 pass, dùng controller probe chỉ trong test |
| ShipmentIdempotencyIT | 6 pass, gồm race cùng/khác payload, atomic rollback, collision retry, replay và transaction boundary |
| `python test/tools/verify_packaging.py` | Hai JAR Java 21/layout đúng, không chứa test artifacts |
| Khởi động lại backend trực tiếp bằng Java | Flyway validate hai migration, schema hiện hành, Hibernate validate/startup thành công |
| `GET http://localhost:18080/api/v1/public/health` | HTTP 200, `{"status":"UP"}` |

Thông tin MySQL đọc từ .env và truyền riêng qua TEST_DB_* cho tiến trình test, không in hoặc commit mật khẩu; không đổi quyền tài khoản, không chạy test trên customer_dev. Maven/test chạy tuần tự với heap 256 MB mỗi JVM.

Log verification: `.cache/task12-full-verify.log`, `.cache/task12-development-startup.log`. Không commit cache/log/artifact.

Chưa có controller tạo đơn thật, counter API, payment hoặc scheduler. Bước tiếp theo: Task 13 — API nội bộ tạo đơn tại quầy và phân quyền service.
