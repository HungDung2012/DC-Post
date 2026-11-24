# DC Post — Customer

Phân hệ Customer của hệ thống quản lý vận đơn Microservices. Đặc tả:
[PROJECT_SPEC.md](PROJECT_SPEC.md),
[thiết kế](docs/superpowers/specs/2026-11-24-customer-service-design.md),
[kế hoạch](docs/superpowers/plans/2026-11-24-customer-service-implementation.md).

## Trạng thái

Đã triển khai Backend đăng ký, đăng nhập, refresh/logout, bảo mật HTTP, hồ sơ và đổi
mật khẩu (Tasks 01–09). Access token có hạn 24 giờ; refresh có hạn 7 ngày.
Frontend hiện là trang khởi tạo, chưa có giao diện nghiệp vụ. Task 11 đã có schema
shipments/shipment_items, entity snapshot và mã tracking; API tạo đơn, thanh toán,
scheduler còn ở các task tiếp theo. Task 10 đã có Interface/HTTP Adapter và mock
server độc lập; giao thức mô phỏng chưa phải API Contract Go chính thức.
Xem [bằng chứng checkpoint](docs/verification/2026-11-24-identity-checkpoint.md)
và [cách gọi API xác thực](docs/references/customer-auth-api.md).
Checkpoint mới nhất: [schema vận đơn, 103 test trên Java/MySQL cục bộ](docs/verification/2026-11-24-shipment-checkpoint.md).

## Yêu cầu môi trường

- JDK 21 LTS, Maven 3.9, Node >=22.12, npm và MySQL 8.4 cài trực tiếp.
- Development và kiểm thử không cần Docker. Theo yêu cầu ngày 24/11/2026,
  không chạy Docker trên máy hiện tại do giới hạn bộ nhớ.
- Xem [phiên bản đã xác minh](docs/references/toolchain.md).

Với môi trường Windows hiện tại, chọn JDK 21 chỉ cho terminal:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12.1'
$env:Path = "$env:JAVA_HOME/bin;" + $env:Path
mvn -version
```

## Build

Chạy từ root repository:

```powershell
mvn -f customer-service/pom.xml clean install
npm.cmd --prefix customer-web ci
npm.cmd --prefix customer-web run typecheck
npm.cmd --prefix customer-web run build
```

Backend có plain JAR cho module test phụ thuộc và JAR `-exec.jar` để chạy.
Maven cache ở `.cache/maven`, không đưa vào Git.
`.mvn/jvm.config` giới hạn heap Maven 256 MB; JVM kiểm thử cũng giới hạn 256 MB
và 2 CPU. Đây là giới hạn heap mỗi tiến trình, không phải tổng RAM toàn dự án.

## Chạy development

Sao chép `.env.example` thành `.env` nếu chưa có, thay DB_URL/DB_USERNAME/DB_PASSWORD
bằng thông tin MySQL cài trên máy và thay các key minh họa bằng giá trị cục bộ.
Không commit `.env`. Chưa có merchant VNPay vẫn build được; tích hợp Sandbox thật cần
credential và IPN URL được xác minh ở giai đoạn thanh toán.

```powershell
# Chỉ thực hiện lần đầu, khi chưa có .env:
Copy-Item .env.example .env
# Sửa thông tin MySQL trong .env trước khi chạy.
# Nạp file KEY=value thuần; không dùng dấu nháy bao quanh giá trị.
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
java -Xms32m -Xmx256m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -jar customer-service/target/customer-service-0.1.0-SNAPSHOT-exec.jar
```

MySQL phải đang chạy, có database `customer_dev` và tài khoản ứng dụng tương ứng.
Service Windows hiện có tên `MySQL84`; có thể bật trong Services nếu đang dừng.
Không dùng lại credential Docker cũ nếu MySQL cục bộ chưa có tài khoản đó.

Mở terminal khác cho Frontend:

```powershell
npm.cmd --prefix customer-web run dev
```

Frontend: http://localhost:5173. Backend: http://localhost:18080 (SERVER_PORT trong .env).
Database development: `localhost:3306/customer_dev`.
VITE_API_BASE_URL được đưa vào frontend lúc build, không được dùng để lưu secret.
API key nội bộ được cấu hình riêng bằng INTERNAL_COUNTER_API_KEY và
INTERNAL_CASHIER_API_KEY. Để trống thì service tương ứng không được cấp quyền.
OPENAPI_ENABLED điều khiển Swagger/OpenAPI; mặc định bật cho development.

Spring Boot không tự đọc `.env`; đoạn PowerShell ở trên chỉ nạp biến vào terminal
hiện tại, không in secret hoặc thực thi nội dung file. Có thể dùng cấu hình Environment
Variables của IDE thay thế. Health: `/api/v1/public/health`.
Mock tích hợp chạy bằng `python test/mock-server/server.py`; xem
[hướng dẫn mô phỏng](test/mock-server/README.md). Không cần bật mock để chạy unit test.

## Kiểm thử

Xem [test/README.md](test/README.md). Test chỉ nằm trong `test/`, không nằm trong
source ứng dụng. Build/install lại Backend trước khi chạy module test độc lập.
`mvn -f test/backend/pom.xml test` chạy nghiệp vụ và HTTP adapter trên JVM,
không cần MySQL hoặc Docker. Test database chạy riêng với MySQL cục bộ,
database `customer_test`, cấu hình bằng TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD.

Không cài framework kiểm thử Frontend. Giao diện được kiểm tra bằng typecheck/build
và checklist thủ công trong các giai đoạn triển khai tương ứng.
