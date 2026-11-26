# Checkpoint Task 16 — Phục hồi, đối soát và retry an toàn

Ngày 27/11/2026, nhánh feat/customer-service, tiếp nối Task 15 tại 361d34b.
Chạy JDK 21/MySQL 8.4 cục bộ, không Docker. Không thêm bảng, migration hoặc dependency.

## Phạm vi và giới hạn

- PROCESSING quá hạn hoặc thiếu startedAt chuyển UNKNOWN trước khi tra cứu, không
  tự đưa về PENDING. Service mới tiếp tục từ trạng thái đã lưu MySQL sau restart.
- UNKNOWN được claim lease bằng các trường sync_* hiện có. Claim commit trước HTTP;
  kết quả chỉ ghi khi UNKNOWN/attempt/startedAt còn khớp. Lease hết hạn được claim
  lại; kết quả cũ không ghi đè kết quả mới. Tra cứu không tăng số lần submit.
- FOUND xác nhận SYNCED, kể cả ngân sách submit đã hết. UNKNOWN/lỗi tạm thời hẹn
  tra cứu sau reconciliationDelay; auth/UNSUPPORTED bị BLOCKED với mã lý do.
- NOT_FOUND chỉ cho retry nếu adapter xác minh kết luận có thẩm quyền và phía nhận
  chống trùng cùng khóa ổn định, kể cả request cũ đến muộn. Capability nội bộ
  supportsSafeResubmission mặc định false; HTTP adapter hiện tại giữ false và
  NOT_FOUND bị BLOCKED/SHIPPING_RETRY_UNSUPPORTED. Không tự thiết kế contract Go.
- Chỉ CONNECT_TIMEOUT chắc chắn chưa gửi mới retry trực tiếp. Read timeout, HTTP
  500, dữ liệu sai hoặc kết quả chưa rõ giữ UNKNOWN để đối soát. Không retry mù.
- Retry kiểm tra lại CASH/chưa hủy/ngân sách dưới khóa. Mặc định tối đa 5 lần submit;
  backoff 30 giây, nhân đôi và chặn tại 15 phút. Hẹn từ thời điểm nhận kết quả.
  Không sleep hoặc giữ transaction/khóa qua HTTP. Chỉ lưu mã lỗi đã làm sạch.
- Scheduler chạy recovery trước submit; mỗi lô giới hạn 10 ID, tuần tự, nghỉ 10 giây
  sau chu kỳ. Lỗi worker hoặc scan recovery không chặn xử lý lô gửi mới.
- Processing timeout mặc định 2 phút, reconciliation delay 1 phút. Cấu hình duration
  1 giây–1 ngày, maxAttempts 1–100; processing timeout phải lớn hơn tổng timeout
  connect/read của adapter. Startup từ chối cấu hình không hợp lệ.

VNPAY vẫn bị chặn hoàn toàn đến Task 21 nối payment local đã xác minh PAID vào
candidate/claim/retry eligibility. Không biến lỗi Shipping thành thanh toán FAILED;
nghiệp vụ thanh toán và trạng thái hiển thị đang xử lý giữ theo thiết kế đã duyệt.
Chưa tuyên bố exactly-once xuyên service khi chưa xác minh contract Shipping thật.

Tài liệu cấu hình và bảng kết quả:
[shipment-synchronization.md](../references/shipment-synchronization.md).

## TDD và review

Các vòng RED đều thất bại ở assertion, không có lỗi compile/setup:

| Vòng | RED | GREEN |
|---|---|---|
| Backoff và cấu hình | 2 tests, 2 failures, 0 errors | 2 pass; bổ sung guard timeout thành 3 pass |
| PROCESSING bị gián đoạn | 1 test, 1 failure, 0 errors | 1 pass |
| Lookup và quyết định retry | 7 tests, 5 failures, 0 errors | 7 pass |
| Retry an toàn/ngân sách | 11 tests, 4 failures, 0 errors | 11 pass |
| Scheduler recovery trước submit | 5 tests, 1 failure, 0 errors | 5 pass |
| Candidate recovery đến hạn/giới hạn | 12 tests, 1 failure, 0 errors | 12 pass |

Regression bổ sung đã pass trong ShipmentRecoveryIT (17 tests): hai worker chỉ
một lookup, lease còn hạn/hết hạn, response và lỗi từ worker cũ không ghi đè,
đổi cancellation/payment khi lookup đang chạy chặn resend, FOUND sau hết ngân sách
vẫn SYNCED, safe NOT_FOUND không phục hồi ngân sách. Dùng Clock cố định và trạng thái
đã lưu MySQL để mô phỏng restart; không kill ứng dụng để tạo kịch bản test.

HTTP loopback thật dùng ShippingHttpAdapter tới server JVM cổng ngẫu nhiên: HTTP500,
outcome sai và read timeout sau khi server đã nhận POST đều UNKNOWN; lookup FOUND
sau đó thành SYNCED, chỉ một POST. Latch điều khiển thời điểm phản hồi; kiểm tra
HTTP ngoài transaction. Capability true chỉ được mô phỏng trong test đủ điều kiện.

Review độc lập chỉ đọc production, tests và tài liệu: không có Critical/Important.
Đã review bổ sung các regression concurrency, stale lease, cancellation và HTTP thật.
Reviewer không chạy tests; kết quả dưới đây từ verification tại phiên triển khai.

## Verification

| Kiểm tra | Kết quả |
|---|---|
| Install Backend sau thay đổi source cuối | BUILD SUCCESS, JDK 21 |
| Focused regression | 10 unit + 31 MySQL IT pass |
| `mvn -B -ntp -f test/backend/pom.xml verify -Pintegration` | 95 unit/HTTP + 98 MySQL IT = 193, 0 failures/errors/skips |
| ShipmentRecoveryIT | 17 pass, gồm HTTP loopback thật |
| ShipmentSyncClaimIT | 14 pass |
| `python test/tools/verify_packaging.py` | Hai JAR Java 21 đúng layout, không test artifacts |
| `git diff --check` | Không lỗi whitespace |
| Backend chạy trực tiếp JAR mới | Startup thành công, Flyway validate 2 migrations, schema hiện hành |
| GET http://localhost:18080/api/v1/public/health | HTTP 200, status UP |
| GET /api/v1/customer/shipments không JWT | HTTP 401 |

MySQL customer_test tách customer_dev. Test config/mocks/fixtures/scripts chỉ nằm
trong test/. Credential nạp cục bộ, không in hoặc commit .env. Maven và test chạy
tuần tự, heap mỗi JVM 256 MB, 2 CPU; backend dùng Serial GC. Không chạy Docker.

Log cục bộ không commit: .cache/task16-policy-red.log, task16-policy-green.log,
task16-recovery-red.log, task16-lookup-red.log, task16-retry-red.log,
task16-scheduler-red.log, task16-scan-red.log, task16-scheduler-green.log,
task16-regression.log, task16-install.log, task16-full-verify.log,
task16-development-startup.log (các tên rút gọn cùng nằm trong .cache/).

Bước tiếp theo: Task 17 — tài liệu API Customer và error contract.
