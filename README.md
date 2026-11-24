# DC Post — Customer

Phân hệ Customer của hệ thống quản lý vận đơn Microservices. Đặc tả:
[PROJECT_SPEC.md](PROJECT_SPEC.md),
[thiết kế](docs/superpowers/specs/2026-11-24-customer-service-design.md),
[kế hoạch](docs/superpowers/plans/2026-11-24-customer-service-implementation.md).

## Trạng thái

Đã triển khai Backend đăng ký, đăng nhập, refresh/logout, bảo mật HTTP, hồ sơ và đổi
mật khẩu (Tasks 01–09). Access token có hạn 24 giờ; refresh có hạn 7 ngày.
Frontend hiện là trang khởi tạo, chưa có giao diện nghiệp vụ. Vận đơn, thanh toán,
scheduler và tích hợp service ngoài còn ở các task tiếp theo.
Xem [bằng chứng checkpoint](docs/verification/2026-11-24-identity-checkpoint.md)
và [cách gọi API xác thực](docs/references/customer-auth-api.md).

## Yêu cầu môi trường

- JDK 21 LTS, Maven 3.9, Node >=22.12, npm và Docker Compose.
- Docker Desktop phải đang chạy nếu sử dụng MySQL bằng container.
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

## Chạy development

Sao chép `.env.example` thành `.env`, thay password/key minh họa bằng giá trị cục bộ.
Không commit `.env`. Chưa có merchant VNPay vẫn build được; tích hợp Sandbox thật cần
credential và IPN URL được xác minh ở giai đoạn thanh toán.

```powershell
Copy-Item .env.example .env # Chỉ thực hiện lần đầu, khi chưa có .env.
docker compose up -d --build
```

Frontend: http://localhost:5173. Backend: http://localhost:18080.
Container Backend luôn dùng port 8080; BACKEND_PORT đổi cổng publish trên máy host.
Database development: `localhost:3306/customer_dev`.
Vite cục bộ có thể chạy bằng `npm.cmd --prefix customer-web run dev`.
VITE_API_BASE_URL được đưa vào frontend lúc build, không được dùng để lưu secret.
API key nội bộ được cấu hình riêng bằng INTERNAL_COUNTER_API_KEY và
INTERNAL_CASHIER_API_KEY. Để trống thì service tương ứng không được cấp quyền.
OPENAPI_ENABLED điều khiển Swagger/OpenAPI; mặc định bật cho development.

Để chạy Backend ngoài container, cung cấp các biến môi trường trong `.env` cho tiến trình
Java; Spring Boot không tự đọc file `.env`. Có thể chạy MySQL riêng bằng
`docker compose up -d --wait mysql`, rồi chạy JAR với biến môi trường đã thiết lập.

## Kiểm thử

Xem [test/README.md](test/README.md). Test chỉ nằm trong `test/`, không nằm trong
source ứng dụng. Build/install lại Backend trước khi chạy module test độc lập.
Database test ở cổng 3307, database `customer_test`, volume riêng.

Không cài framework kiểm thử Frontend. Giao diện được kiểm tra bằng typecheck/build
và checklist thủ công trong các giai đoạn triển khai tương ứng.
