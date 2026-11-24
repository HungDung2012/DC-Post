# Kiểm thử Customer

Toàn bộ mã, cấu hình, fixture, mock và script kiểm thử nằm trong `test/`.
Module Maven này không được đưa vào dependency hoặc image của ứng dụng.

Trong PowerShell, chọn JDK 21 cho terminal hiện tại:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12.1'
$env:Path = "$env:JAVA_HOME/bin;" + $env:Path
mvn -version
mvn -f customer-service/pom.xml clean install
docker compose -f test/compose.yml up -d --wait mysql-test
mvn -f test/backend/pom.xml test
mvn -f test/backend/pom.xml verify -Pintegration
```

Unit test dùng tên class `*Test`; integration test dùng `*IT`. Mỗi method có Javadoc,
tên `Test_<methodName>_001` và số tăng dần. Build/install lại Backend sau khi sửa source
để module test không chạy với artifact cũ.

MySQL test ở `localhost:3307/customer_test`, khác development ở cổng 3306.
Credential trong `test/compose.yml` chỉ phục vụ database kiểm thử cục bộ.
Không trỏ test vào database development hoặc production. Không dùng `down -v`
nếu không chủ động muốn xóa dữ liệu kiểm thử.

Hiện có 45 unit/HTTP tests và 27 integration tests cho danh tính, xác thực và hồ sơ.
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

Surefire/Failsafe nạp Mockito bằng Java agent của module test, không đưa agent hoặc
Mockito vào JAR ứng dụng. DEBUG=false được đặt riêng cho tiến trình kiểm thử để
không kế thừa biến DEBUG của máy. HTTP tests tắt việc in toàn request khi thất bại,
tránh đưa token/cookie vào output.

Kiểm tra cách đóng gói sau khi build Backend:

```powershell
python test/tools/verify_packaging.py
```
