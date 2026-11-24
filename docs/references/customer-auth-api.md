# Customer authentication và hồ sơ

Đây là API của Customer Backend ở checkpoint Tasks 01–09, không phải contract của
service Go bên ngoài. Base URL development: `http://localhost:18080`.

| Method | Path | Xác thực và kết quả |
|---|---|---|
| GET | `/api/v1/public/health` | Kiểm tra tiến trình đã khởi động; `{ "status": "UP" }` |
| GET | `/api/v1/auth/csrf` | Trả `token` và cookie `XSRF-TOKEN` |
| POST | `/api/v1/auth/register` | Origin + CSRF; 201, tài khoản chỉ có CUSTOMER |
| POST | `/api/v1/auth/login` | Origin + CSRF; 200, access token và refresh cookie |
| POST | `/api/v1/auth/refresh` | Origin + CSRF + refresh cookie; xoay refresh |
| POST | `/api/v1/auth/logout` | Origin + CSRF + Bearer; 204, thu hồi phiên |
| GET | `/api/v1/customer/profile` | Bearer; hồ sơ tài khoản đang đăng nhập |
| PUT | `/api/v1/customer/profile` | Bearer; cập nhật tên, điện thoại, địa chỉ |
| POST | `/api/v1/customer/change-password` | Bearer; 204, đổi mật khẩu và thu hồi phiên |

Trước thao tác auth, gọi GET csrf với credential, giữ cookie và gửi `token` nhận
được bằng header `X-XSRF-TOKEN`. POST auth phải có `Origin` thuộc allowlist, mặc
định `http://localhost:5173`. Client HTTP cục bộ cũng phải gửi Origin cho auth writes.
Request Customer dùng `Authorization: Bearer <accessToken>`.

Body đăng ký: `fullName`, `email`, `phone`, `password`. Login: `email`, `password`.
PUT profile: `fullName`, `phone`, `address` (nullable). Đổi mật khẩu:
`currentPassword`, `newPassword`. User ID lấy từ principal; không lấy từ body.
Email đăng nhập hiện chỉ đọc trong hồ sơ.

Quy tắc đầu vào hiện tại: tên tối đa 255 ký tự; địa chỉ tối đa 500 ký tự; email
trim/lowercase; điện thoại Việt Nam 10–11 chữ số bắt đầu bằng 0, chuẩn hóa tiền tố
`+84` về `0` và bỏ khoảng trắng/dấu phân cách thông thường. Mật khẩu tối thiểu 8
ký tự và tối đa 72 byte UTF-8 theo giới hạn BCrypt; không trim mật khẩu.

Access token có hạn 24 giờ. Login mới thay phiên cũ. Refresh ngẫu nhiên 256 bit có
hạn 7 ngày, xoay sau mỗi lần dùng; MySQL chỉ lưu SHA-256. Cookie `refresh_token` có
HttpOnly, SameSite=Lax, Path=/api/v1/auth và Secure theo AUTH_COOKIE_SECURE.
Frontend phải giữ access trong memory, dùng credential cho cookie và chỉ refresh
một lần tại một thời điểm. Giao diện này chưa được triển khai tại checkpoint.

Logout/đổi mật khẩu tăng auth_version và xóa refresh trong cùng transaction. Backend
kiểm tra lại active/role/version cho mỗi request Customer; access cũ bị từ chối ngay.
Sau đổi mật khẩu, client cần xóa access đang giữ và đăng nhập lại.

Lỗi dùng Problem Details với `code`, `correlationId` và `errors` khi có lỗi từng
trường. Header `X-Correlation-ID` khớp ID trong body. Trùng email/phone: 409;
validation/mật khẩu hiện tại sai: 400; chưa xác thực: 401; quyền/Origin/CSRF: 403;
quá hạn mức: 429 kèm Retry-After. Không echo password hoặc token trong lỗi.

Auth writes (trừ logout) và public tracking được giới hạn 20 request/phút/IP trực
tiếp trong một tiến trình; tối đa 10.000 bucket có TTL. Không tin X-Forwarded-For.

API nội bộ dùng `X-API-Key`, không dùng JWT Customer. Các permission hiện chuẩn bị
cho `POST /api/v1/internal/shipments` và
`POST /api/v1/internal/shipments/{id}/cash-confirmations`; controller nghiệp vụ sẽ
được triển khai ở task tương ứng. Key riêng cho `counter` và `cashier`, mặc định
trống nên bị từ chối. Tên principal là cấu hình quyền của Customer, không phải
khẳng định contract hay tên service của thành viên khác.
