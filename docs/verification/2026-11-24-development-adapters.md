# Task 10 và chuyển sang Java cục bộ

Ngày 24/11/2026, nhánh `feat/customer-service`.

## Phạm vi đã triển khai

- PricingClient, WarehouseClient, ShippingClient và DTO nội bộ Customer; HTTP adapters dùng Spring RestClient và endpoint/timeouts từ cấu hình.
- Giao thức `/dev/` chỉ phục vụ mô phỏng; chưa phải API Contract Go, chưa xác minh service thật.
- Phí VND parse BigDecimal chính xác, kiểm tra số nguyên không âm, currency và thời điểm nhận bằng Clock. Không làm tròn phần lẻ.
- Mutation lỗi giữ UNKNOWN_OUTCOME và loại lỗi gốc; không retry tự động. Lookup phân biệt FOUND/NOT_FOUND/UNKNOWN/UNSUPPORTED; HTTP lỗi không biến thành NOT_FOUND.
- Tracking public events tách private details; không tạo enum trạng thái vận chuyển chính thức.
- Python mock/fixture/smoke hoàn toàn trong `test/`, kiểm tra idempotency, timeout sau mutation, invalid JSON, duplicate và lỗi dependency. Không thêm bảng database.

## Yêu cầu môi trường mới

Trong lượt kiểm thử trước, Windows báo thiếu bộ nhớ ảo/native memory; Docker build cũng mất kết nối với engine. Chủ dự án yêu cầu không dùng Docker khi code/test. Đã chuyển hướng dẫn sang Java/Maven, Vite và MySQL cài trực tiếp; không tự khởi động Docker nữa. File Compose/Dockerfile cũ giữ để tham chiếu.

`.mvn/jvm.config` giới hạn Maven heap 256 MB, 2 CPU, Serial GC; Surefire/Failsafe áp dụng giới hạn tương tự cho tiến trình test. Đây là heap mỗi JVM, không bảo đảm tổng RAM chỉ 256 MB. Các bước build/test chạy tuần tự.

`DevelopmentAdapterIT` đổi thành `DevelopmentAdapterTest` để chạy mặc định cùng test nghiệp vụ, không cần database hoặc Python server. `*IT` chỉ opt-in bằng `-Pintegration`; cấu hình TEST_DB_* dùng MySQL cục bộ, database `customer_test` tách `customer_dev`. IdentitySchemaIT cũng đọc cùng biến và dùng catalog thực tế cho metadata.

## Verification thực tế

Các lệnh chạy từ root bằng JDK 21.0.12.1, không Docker:

| Lệnh | Kết quả |
|---|---|
| `mvn -B -ntp -f customer-service/pom.xml install` | BUILD SUCCESS, plain JAR và exec JAR |
| `mvn -B -ntp -f test/backend/pom.xml clean test` | 63 tests, 0 failures/errors/skipped |
| `mvn -B -ntp -f test/backend/pom.xml test` sau sửa cấu hình IdentitySchemaIT | 63 tests, 0 failures/errors/skipped; biên dịch cả test MySQL |
| `python test/mock-server/server.py --help` | CLI hoạt động |
| Khởi động mock rồi `Invoke-WebRequest` health và warehouse | HTTP 200, fixture đúng |
| `python test/mock-server/smoke.py` | 34 checks qua 9 tình huống |
| `python test/tools/verify_packaging.py` | Hai JAR đúng Java 21/layout, không chứa test artifacts |
| `git diff --check` | Không có lỗi whitespace |

63 tests gồm 45 test identity/auth/profile trước đó và 18 test HTTP adapter. RED được quan sát trước xử lý HTTP; review phát hiện precision JSON bị làm tròn, hai regression tiny fraction/large integer đã RED rồi GREEN sau khi bật USE_BIG_DECIMAL_FOR_FLOATS.

Code review đã kiểm tra adapter và thay đổi môi trường, không còn Critical/Important trong phạm vi đã review. Lỗi hardcode cổng 3307 trong IdentitySchemaIT cũng được sửa và review lại.

## Chưa xác minh trong môi trường mới

- MySQL84 trên máy đã cài nhưng service đang dừng tại thời điểm kiểm tra. Chưa có thông tin tài khoản/database test cục bộ nên chưa chạy lại 27 test MySQL hoặc khởi động ứng dụng với database này.
- Các test MySQL từng pass tại checkpoint trước là bằng chứng lịch sử, không chứng minh cấu hình MySQL cục bộ hiện tại đã hoạt động.
- Không tuyên bố đã tích hợp API Go, VNPay Sandbox hoặc hoàn tất Tasks 11–34.

Log cục bộ nằm trong `.cache/local-java-build.log`, `.cache/local-java-tests.log`, không commit. Hướng dẫn chạy trực tiếp ở README và test/README.
