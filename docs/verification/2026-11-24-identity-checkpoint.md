# Checkpoint Tasks 01–09 — Customer identity

Ngày xác minh: 24/11/2026. Nhánh: `feat/customer-service`.
Design và implementation plan đã được chủ dự án duyệt trước khi triển khai.

## Kết quả

| Phần kiểm tra | Bằng chứng |
|---|---|
| Backend | Maven install thành công với JDK 21.0.12.1 |
| Packaging | Plain JAR và exec JAR đúng layout, bytecode major 65, không chứa test artifacts |
| Unit/HTTP tests | 45 tests, 0 failure, 0 error, 0 skipped |
| Integration tests | 27 tests, 0 failure, 0 error, 0 skipped; MySQL 8.4.8, customer_test:3307 |
| Frontend scaffold | npm typecheck và build thành công |
| Compose development | MySQL, customer-service, customer-web đều Healthy sau build mới |
| HTTP smoke | Backend `/api/v1/public/health` trả 200 và UP; Frontend :5173 trả 200 |
| Secret/build output | .env, .cache, target, node_modules, dist được Git ignore |

Lệnh tái lập từ root, sau khi chọn JAVA_HOME là JDK 21:

```powershell
mvn -B -ntp -f customer-service/pom.xml install
docker compose -f test/compose.yml up -d --wait mysql-test
mvn -B -ntp -f test/backend/pom.xml verify -Pintegration
python test/tools/verify_packaging.py
npm.cmd --prefix customer-web run typecheck
npm.cmd --prefix customer-web run build
docker compose up -d --build --wait
```

Log Maven cục bộ gần nhất: `.cache/identity-verify.log` (ignored). Báo cáo chi tiết
trong `test/backend/target/surefire-reports` và `failsafe-reports` (ignored).

## TDD và review

- Migration: 3 ca ban đầu RED do thiếu schema/constraint, sau đó GREEN; mở rộng
  seed idempotent, FK và kiểm tra ghi dữ liệu trùng trên MySQL.
- Đăng ký: RED cho kết quả/validation/duplicate/race, GREEN với BCrypt thật trong
  unit test. HTTP RED 501, GREEN 201/400/409 và DTO không chứa hash/token.
- JWT/session: RED cho phát hành/xác minh, thay phiên, refresh/logout, GREEN.
  Refresh đồng thời trên MySQL chỉ có một request thành công.
- HTTP security: RED với security mặc định, GREEN cho JWT, service permissions,
  Origin/CSRF/CORS, refresh/logout và rate limit. Các probe controller chỉ ở test/.
- Hồ sơ: 8 ca service RED, HTTP RED 404 rồi GREEN. Thêm kiểm tra duplicate-key khi
  flush và integrity lỗi khác không bị đổi nhầm thành conflict.
- Regression JWT: token ký đúng nhưng thiếu iat ban đầu vẫn được chấp nhận do
  Spring tự điền iat=exp-1. Kiểm tra raw claims trước converter đã sửa lỗi; test
  thiếu iat/exp, issuer/sub/version sai hiện đều đạt.
- Registration rollback: role fixture không tồn tại gây FK lỗi trên MySQL thật;
  user được rollback khi caller không mở transaction ngoài.
- Review độc lập theo Superpowers không còn Critical/Important trong phạm vi
  checkpoint. Các góp ý correlation ID, lỗi CORS và coverage đã được xử lý và kiểm thử.

## Giới hạn và phần tiếp theo

Mới có 3 bảng danh tính và Flyway schema history. Chưa tạo 4 bảng nghiệp vụ còn lại;
thiết kế vẫn giữ tổng cộng 7 bảng nghiệp vụ. Không có bảng session hoặc integration_tasks.

Frontend chưa có form đăng nhập/hồ sơ hoặc màn hình nghiệp vụ. Chưa có vận đơn,
Pricing/Warehouse/Shipping adapter, scheduler, thanh toán/hoàn tiền VNPay hoặc
kiểm thử Sandbox thật. Không dùng kết quả checkpoint để tuyên bố hệ thống hoàn tất.

Task tiếp theo là 10 (Interface/Adapter và HTTP mock server trong test/), sau đó
schema/luồng vận đơn theo plan. Contract service Go và credential VNPay thật vẫn
là dependency tích hợp cần xác minh ở các task tương ứng.

Host port Backend dùng 18080 vì Windows từ chối bind 8080; container vẫn dùng 8080.
Flyway 11.20.3 được pin để hỗ trợ MySQL 8.4. Môi trường development cùng site;
triển khai HTTPS cần bật AUTH_COOKIE_SECURE. Internal keys trống sẽ từ chối truy cập.
