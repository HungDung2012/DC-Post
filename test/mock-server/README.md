# Mock HTTP cho development

Đây là giao thức mô phỏng tạm của Customer, **không phải API Contract Go đã thống nhất**. Task 32 phải cập nhật mapping HTTP sau khi nhóm cung cấp contract thật. Không dùng các route `/dev/` này để tuyên bố đã tích hợp service Go.

```powershell
python test/mock-server/server.py --help
python test/mock-server/smoke.py
python test/mock-server/server.py --port 8090
Invoke-WebRequest http://127.0.0.1:8090/health
Invoke-WebRequest http://127.0.0.1:8090/dev/warehouse/locations
```

Ứng dụng chạy trên host dùng `PRICING_BASE_URL`, `WAREHOUSE_BASE_URL`, `SHIPPING_BASE_URL` bằng `http://localhost:8090`. Các biến này là cấu hình endpoint thông thường; ứng dụng không có mock profile hoặc fallback. Timeout mặc định connect 3 giây, read 10 giây.

Backend và mock chạy trực tiếp trên máy; không cần Docker. Giữ mặc định bind `127.0.0.1` để chỉ phục vụ development cục bộ.

| Method | Route mô phỏng | Dữ liệu |
|---|---|---|
| POST | `/dev/pricing/quote` | FeeRequest → amount số nguyên không âm, currency VND; thời điểm nhận do Customer ghi |
| GET | `/dev/warehouse/locations` | locations với id dạng chuỗi opaque, name, address |
| POST | `/dev/shipping/submissions` | ShipmentSubmission, header Idempotency-Key → outcome CONFIRMED/REJECTED/UNKNOWN |
| GET | `/dev/shipping/submissions/{trackingCode}` | status FOUND/NOT_FOUND/UNKNOWN/UNSUPPORTED |
| POST | `/dev/shipping/submissions/{trackingCode}/cancel` | reason, header Idempotency-Key → outcome |
| GET | `/dev/shipping/tracking/{trackingCode}` | sourceStatus, updatedAt, publicEvents tách privateDetails |

Schema DTO ở source Customer là nhu cầu nội bộ, không ràng buộc wire format Go. Mã trạng thái `DEVELOPMENT_STATUS` chỉ là fixture, không định nghĩa quy trình vận chuyển. Public controller tương lai phải map allowlist; không trả toàn bộ ShipmentTracking chứa privateDetails.

Chọn tình huống bằng `--scenario success|timeout|invalid-json|duplicate|rejected|unknown|unsupported|unavailable|unauthorized`. Ví dụ:

```powershell
python test/mock-server/server.py --scenario timeout --delay 12
python test/mock-server/server.py --scenario invalid-json
python test/mock-server/server.py --scenario duplicate
```

Timeout áp dụng mutation trước khi trì hoãn response, mô phỏng trường hợp phía ngoài đã nhận nhưng Customer chưa biết kết quả. Không tự retry mutation. Lỗi mutation giữ UNKNOWN_OUTCOME cùng loại lỗi gốc; lỗi HTTP 401/403 phân loại UNAUTHORIZED. Lookup chỉ coi NOT_FOUND khi response nói rõ; lỗi HTTP không được diễn giải thành không tồn tại. `unsupported` trả capability UNSUPPORTED rõ ràng.

Mock lưu trạng thái và idempotency trong bộ nhớ, tách scope submit/cancel: cùng key/cùng payload trả lại kết quả; khác payload trả 409. `duplicate` trả CONFIRMED kèm dấu hiệu duplicate; không tạo đơn thứ hai. Restart mock xóa trạng thái mô phỏng. Không dùng nó để chứng minh tính bền vững hoặc đối soát của service thật. Log không chứa body/key/PII; không có API key giả định cho service ngoài.

Fixtures nằm hoàn toàn trong `test/mock-server/fixtures/`. Test Java `DevelopmentAdapterTest` dùng HTTP server cục bộ trên cổng ngẫu nhiên, không cần Docker, MySQL, bật Python server hoặc gọi service thật. Chạy cùng test nghiệp vụ bằng `mvn -f test/backend/pom.xml test`.
