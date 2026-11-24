# Checkpoint Task 11 — Shipment persistence

Ngày 24/11/2026, nhánh `feat/customer-service`. Chạy trực tiếp bằng JDK 21/MySQL cục bộ, không Docker.

## Phạm vi

- Migration V2 chỉ thêm shipments và shipment_items: hiện có năm bảng nghiệp vụ identity3 + shipment2, cùng metadata Flyway. Không tạo integration_tasks, auth_sessions hoặc bảng vận chuyển bên ngoài.
- customer_id BIGINT liên kết users; warehouse/actor INT tạm theo chỉ đạo mới nhất của chủ dự án, chưa được nhóm xác nhận. Không có FK xuyên service. Task 32 phải đối chiếu lại kiểu thật; Task 12 kiểm tra miền khi ánh xạ user_id sang actor INT.
- Snapshot người gửi/nhận, parcel, phí, phương thức thanh toán và items; JPA cascade lưu items. Đổi profile không sửa snapshot đã lưu.
- Sync/cancellation lưu riêng; CASH bắt đầu PENDING, VNPAY bắt đầu WAITING_PAYMENT. Chưa triển khai gửi đơn hoặc xử lý thanh toán.
- Tracking DC + 128 bit SecureRandom, unique ở database; kiểm tra collision bằng giá trị cố định. Dịch vụ tạo đơn và retry có giới hạn thuộc Task 12.
- Unique theo creation_request_scope/key, so sánh phân biệt hoa thường. Index customer/time, scheduler và lịch hủy; không thêm index hiển thị chưa cần.

## TDD và review

Đã quan sát assertion RED cho generator, schema chưa tồn tại và constructor snapshot/state chưa triển khai, sau đó GREEN sau implementation. Test schema dùng JDBC thật, dữ liệu trong transaction rollback; test JPA dùng transaction rollback.

Review phát hiện DECIMAL(15,2) có thể làm tròn trước CHECK. Regression với phí 25000.001 đã RED rồi GREEN khi domain từ chối phí VND có phần lẻ trước lưu. Giữ đúng DECIMAL trong spec, không tự làm tròn. Database CHECK bảo vệ các giá trị đã được biểu diễn theo scale cột; không thay thế validation đầu vào.

Reviewer xác nhận không còn Critical/Important trong phạm vi Task 11. Test tập bảng kiểm tra chính xác năm bảng nghiệp vụ, loại Flyway metadata.

## Verification

| Kiểm tra | Kết quả thực tế |
|---|---|
| Build/install application | BUILD SUCCESS, JDK 21 |
| `mvn -B -ntp -f test/backend/pom.xml verify -Pintegration` | 64 unit/HTTP + 39 integration = 103, 0 failures/errors/skips |
| TrackingCodeGeneratorTest | 1 pass |
| ShipmentSchemaIT | 8 pass: channel/customer/pickup, parcel, VND, unique, local FK, INT metadata, items |
| ShipmentPersistenceIT | 4 pass: snapshot/items, VNPay waiting, profile independence, fractional-fee rejection |
| `python test/tools/verify_packaging.py` | Hai JAR đúng Java 21/layout, không có test artifacts |
| Backend restart bằng Java, heap 256 MB | Flyway áp dụng V2 vào customer_dev, Hibernate validate và startup thành công |
| `GET http://localhost:18080/api/v1/public/health` | 200, status UP |

Test chạy trên schema customer_test riêng, cùng MySQL cục bộ với customer_dev; không chạy test vào customer_dev. Lượt xác minh dùng thông tin tài khoản đã có trong .env để truy cập schema test, truyền qua TEST_DB_* của tiến trình; không in/commit mật khẩu, không thay quyền tài khoản. Nếu chạy lại với tài khoản test riêng thì cấu hình TEST_DB_* tương ứng theo test/README.

Log cục bộ: `.cache/task11-tracking-red.log`, `.cache/task11-schema-red.log`, `.cache/task11-persistence-red.log`, `.cache/task11-fee-red.log`, `.cache/task11-final.log`, `.cache/task11-development-startup.log`. Log không commit.

Chưa triển khai API tạo vận đơn, idempotent write service, scheduler hoặc payment/refund. Không tuyên bố mock đã xác minh API Go thật. Bước tiếp theo là Task 12.
