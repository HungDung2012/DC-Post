# Kiểm thử Customer

Toàn bộ mã, cấu hình, fixture, mock và script kiểm thử nằm trong `test/`.
Module Maven này không được đưa vào dependency hoặc image của ứng dụng.

Trong PowerShell, chọn JDK 21 cho terminal hiện tại:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12.1'
$env:Path = "$env:JAVA_HOME/bin;" + $env:Path
mvn -version
mvn -f customer-service/pom.xml clean install
mvn -f test/backend/pom.xml test
```

Lệnh `test` chạy test nghiệp vụ và HTTP adapter trên JVM, không cần Docker, MySQL
hoặc Python server. `.mvn/jvm.config` và cấu hình module test giới hạn heap 256 MB,
2 CPU và dùng Serial GC để giảm bộ nhớ. Không chạy Maven và các build khác song song
trên máy hiện tại. Giới hạn heap không bao gồm toàn bộ bộ nhớ native của JVM.

Unit test dùng tên class `*Test`; integration test dùng `*IT`. Mỗi method có Javadoc,
tên `Test_<methodName>_001` và số tăng dần. Build/install lại Backend sau khi sửa source
để module test không chạy với artifact cũ.

Nhóm `*IT` chỉ chạy khi chọn profile `integration`, cần MySQL cài trực tiếp đang chạy.
Mặc định dùng `localhost:3306/customer_test`, tài khoản `customer_test`;
development dùng database riêng `customer_dev`. Chuẩn bị database/tài khoản test
trong MySQL cục bộ, rồi thiết lập biến môi trường trong terminal (không commit mật khẩu):

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/customer_test?connectionTimeZone=UTC'
$env:TEST_DB_USERNAME = 'customer_test'
# Đặt TEST_DB_PASSWORD bằng mật khẩu tài khoản MySQL test của bạn qua IDE hoặc terminal.
mvn -f test/backend/pom.xml verify -Pintegration
```

Không trỏ test vào database development hoặc production. Không cần database server
thứ hai; hai database và tài khoản được phân quyền riêng có thể dùng chung MySQL cục bộ.
`test/compose.yml` chỉ được giữ làm tham chiếu lịch sử, không được tự khởi chạy trên máy này.

Hiện có 95 test nghiệp vụ/HTTP (45 identity/auth/profile, 18 adapter, 1 tracking generator,
18 tạo đơn/biểu diễn lỗi HTTP, 3 query/enrichment, 10 sync/scheduler/config) và 98 integration tests MySQL (27 identity/auth/profile,
8 schema vận đơn, 4 persistence/domain, 6 tạo đơn/idempotency, 9 counter API, 13 Customer shipment API,
14 sync/atomic claim, 17 recovery/retry). Checkpoint Task 16 đã chạy toàn bộ 193 test trên Java/MySQL cục bộ, không Docker.
Các ca mới kiểm tra chuẩn hóa snapshot, xác minh phí, replay trước dependency,
hai request đồng thời cùng key (cùng hoặc khác payload), retry collision tracking,
rollback toàn bộ khi một item lỗi và HTTP dependency chạy ngoài transaction.
CounterShipmentIT kiểm tra endpoint thật qua HTTP filters/MockMvc và MySQL: service source,
guest không có user, chặn liên kết customer chưa xác minh, actor INT không bị cắt số,
scope giữa hai service, replay dữ liệu chuẩn hóa, conflict payload/actor, concurrent create
và phân quyền CASH. Probe tạo counter cũ đã bỏ để không trùng mapping endpoint thật;
probe CASH còn giữ cho security test đến task triển khai xác nhận tiền mặt.
ShipmentCreationHttpTest vẫn chỉ dùng probe để kiểm tra Problem Details;
controller tạo đơn ONLINE thật được kiểm tra trong ShipmentOwnershipIT tại Task 14.
ShipmentOwnershipIT xác minh cả SQL count/search/ownership, phân trang ổn định,
snapshot/items và HTTP enrichment ngoài transaction. ShipmentQueryServiceTest kiểm tra
payload tracking sai vẫn giữ snapshot và từ chối Instant vượt phạm vi MySQL DATETIME.
ShipmentSyncClaimIT xác minh claim commit trước HTTP, worker cạnh tranh dùng barrier/latch,
due/cancellation/VNPAY guard, candidate limit/order, stale result và backoff cho connect
timeout chắc chắn chưa gửi; lỗi không chắc chắn không tự gửi lại. Có một ca gọi ShippingHttpAdapter thật tới HTTP server loopback, kiểm tra
payload/key và PROCESSING đã thấy từ connection MySQL khác. Profile test tắt scheduler
nền; ShipmentSyncSchedulerTest kiểm tra batch độc lập, lỗi một worker không chặn worker
tiếp theo và wiring bật/tắt task bằng ApplicationContextRunner. ShipmentRecoveryIT kiểm tra
restart từ trạng thái MySQL, PROCESSING hết hạn, UNKNOWN có lease, hai worker đối soát
đồng thời, worker cũ không ghi đè, FOUND/NOT_FOUND/UNSUPPORTED, giới hạn attempt và
đổi trạng thái hủy/phương thức thanh toán khi đang tra cứu. HTTP loopback thật mô phỏng
HTTP 500, response sai và read timeout sau khi server nhận POST, sau đó GET FOUND;
chỉ một POST được gửi. ShipmentSyncPropertiesTest kiểm tra backoff có trần, cấu hình
sai và processing timeout phải lớn hơn tổng timeout adapter. Không thêm test hook vào app.
Integration tests kiểm tra migration, rollback khi ghi role lỗi, refresh đồng thời,
CSRF/CORS/API key, thu hồi token và đổi mật khẩu. Các tài khoản fixture được rollback
hoặc xóa sau test. Nhánh lỗi liên kết role dùng repository spy chỉ ở test để đưa ra
role ID không tồn tại; kiểm tra FK và rollback vẫn chạy trên MySQL thật.

Để chạy riêng một integration class không chạy lại unit suite:

```powershell
mvn -f test/backend/pom.xml test-compile failsafe:integration-test failsafe:verify -Pintegration '-Dit.test=IdentitySchemaIT'
```

Lifecycle verify ở trên chạy cả hai suite. Maven được cấu hình thất bại nếu suite được chọn không có test; không coi việc
biên dịch module là đã kiểm thử nghiệp vụ thành công.

Mock Python độc lập: `python test/mock-server/smoke.py`. Test
`DevelopmentAdapterTest` tự bật HTTP server JVM trên cổng ngẫu nhiên, chạy trong
suite mặc định. Chưa có contract Go thì các test này chỉ xác minh giao thức mô phỏng.

Surefire/Failsafe nạp Mockito bằng Java agent của module test, không đưa agent hoặc
Mockito vào JAR ứng dụng. DEBUG=false được đặt riêng cho tiến trình kiểm thử để
không kế thừa biến DEBUG của máy. HTTP tests tắt việc in toàn request khi thất bại,
tránh đưa token/cookie vào output.

Kiểm tra cách đóng gói sau khi build Backend:

```powershell
python test/tools/verify_packaging.py
```
