# Checkpoint Task 13 — Counter API và phân quyền

Ngày 26/11/2026, nhánh feat/customer-service. Tiếp nối checkpoint Task 12; chạy trực tiếp JDK 21/MySQL 8.4 cục bộ, không Docker.

## Phạm vi

- POST /api/v1/internal/shipments yêu cầu X-API-Key của service có COUNTER_SHIPMENT_CREATE và Idempotency-Key. JWT Customer không thay thế API key.
- CounterShipmentRequest bọc dữ liệu shipment đã có, ID nhân viên createdBy dương trong miền INT và customerId phải null. Chỉ COUNTER/DROP_OFF, không tạo user cho Guest, không ghép tài khoản theo phone/email.
- created_by_source chỉ lấy từ ServicePrincipal đã xác thực; không lấy nguồn do payload tự khai báo. Giữ customer_id=NULL. Kiểu warehouse/actor vẫn tạm INT, phải đối chiếu khi tích hợp thật tại Task 32.
- Dùng chung luồng xác minh phí/bưu cục, chuẩn hóa và validation với ONLINE. Orchestration NEVER, writer bean riêng transaction ngắn; không giữ transaction trong dependency call.
- Scope service:<name> cách ly với customer và service khác. Hash counter gồm createdBy và shipment đã chuẩn hóa. Replay xác minh source/channel/customer/actor/hash trước dependency; recovery race ở ngoài transaction đã rollback. Hash ONLINE giữ nguyên cách biểu diễn Task 12.
- Lưu snapshot/items nguyên tử, CASH=PENDING, VNPAY=WAITING_PAYMENT; không gọi Shipping. CASH_PAYMENT_CONFIRM vẫn tách khỏi quyền tạo counter, chưa triển khai ghi nhận thu tiền.
- Jackson không nhận số thập phân cho Integer để tránh âm thầm cắt actor/quantity; overflow cũng bị từ chối. Không đổi schema/migration, không thêm bảng, không định nghĩa API Go chính thức.
- Bỏ test probe tạo counter cũ để dùng controller thật. SecurityBoundaryIT vẫn xác minh JWT Customer bị từ chối và API key theo quyền; khóa tạo counter hợp lệ nhưng thiếu request được 400 từ controller thật. Probe CASH giữ đến task thanh toán.

API Customer sở hữu được mô tả tại docs/references/customer-counter-api.md; cần thống nhất cơ chế gọi thực tế với nhóm trong Task 32.

## TDD và code review

Assertion RED đầu tiên: controller chưa tồn tại trả 404 thay vì 201/400. GREEN sau controller, dịch vụ counter và writer dùng chung.

Assertion RED bổ sung: customerId khác null bị bỏ qua, JSON actor 77.5 bị parser cắt về 77, cả hai trả 201 ngoài dự kiến. GREEN sau ràng buộc customerId @Null và tắt accept-float-as-int. Không thay đổi test để che lỗi nghiệp vụ. Các test invalid actor kiểm tra null, 0, âm, overflow và thập phân.

Review độc lập không phát hiện Critical/Important. Đề xuất Minor đã được bổ sung vào test: cùng employee/key nhưng đổi nội dung shipment nhận conflict; JSON tương đương sau strip/chuẩn hóa phone/decimal replay cùng ID dù dependencies lỗi. Các test kiểm tra phân quyền có cả SecurityBoundaryIT với JWT Customer thật.

## Verification

| Kiểm tra | Kết quả |
|---|---|
| Build/install application JDK 21 | BUILD SUCCESS |
| `mvn -B -ntp -f test/backend/pom.xml verify -Pintegration` | 82 unit/HTTP + 54 MySQL IT = 136, 0 failures/errors/skips |
| CounterShipmentIT | 9 pass qua filters/controller thật và MySQL |
| SecurityBoundaryIT | 13 pass với counter controller thật |
| `python test/tools/verify_packaging.py` | Hai JAR đúng Java 21/layout, không chứa test artifacts |
| Backend khởi động trực tiếp bằng Java | Flyway validate hai migration, schema hiện hành, Hibernate validate/startup thành công |
| `GET http://localhost:18080/api/v1/public/health` | HTTP 200, status UP |
| `POST /api/v1/internal/shipments` không API key trên backend đang chạy | HTTP 401 |

MySQL customer_test tách khỏi customer_dev. TEST_DB_* chỉ dùng cho tiến trình kiểm thử, đọc thông tin kết nối cục bộ đã có từ .env; không in/commit mật khẩu và không đổi quyền tài khoản. Maven/test tuần tự, heap mỗi JVM 256 MB.

Log cục bộ không commit: .cache/task13-counter-red1.log, .cache/task13-counter-red2.log, .cache/task13-counter-green2.log, .cache/task13-counter-review.log, .cache/task13-full-verify.log, .cache/task13-development-startup.log.

Bước tiếp theo: Task 14 — Customer API tạo/list/detail vận đơn và giới hạn quyền sở hữu. Scheduler, thu tiền CASH, payment/refund và tích hợp thật còn ở các task sau.
