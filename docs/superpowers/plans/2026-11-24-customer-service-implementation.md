# Customer Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user chooses delegation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng phân hệ Customer theo PROJECT_SPEC và thiết kế đã duyệt, giữ 7 bảng, xử lý thanh toán/đồng bộ an toàn và cách ly toàn bộ kiểm thử trong `test/`.

**Architecture:** React gọi Spring Boot; Backend dùng MySQL riêng và Interface/HTTP Adapter cho dependency ngoài. Scheduler đọc trạng thái trực tiếp trên bảng nghiệp vụ; không thêm bảng hàng đợi. Không giữ database transaction trong thời gian HTTP và không coi timeout là thất bại chắc chắn.

**Tech Stack:** Java 21, Spring Boot, Maven, MySQL cài trực tiếp, Flyway, Spring Security/JWT, RestClient; React/TypeScript/Vite/Tailwind/shadcn/ui, React Router, TanStack Query, React Hook Form/Zod, Axios, Recharts, Lucide; JUnit 5/Mockito chỉ trong `test/backend`; VNPay Sandbox. Docker/Compose chỉ giữ tham chiếu đóng gói.

---

## 1. Trạng thái, giới hạn và thứ tự thực hiện

**Cập nhật bắt buộc ngày 24/11/2026:** Chủ dự án yêu cầu bỏ Docker khi code/test vì máy thiếu bộ nhớ. Không tự chạy Docker, build image hoặc khởi động container. Backend chạy trực tiếp bằng Java/Maven; Frontend bằng Vite; dùng MySQL đã cài trên máy. `mvn test` chỉ chạy test nghiệp vụ/HTTP không cần database; profile `integration` chạy riêng khi MySQL test cục bộ đã cấu hình. Maven và JVM test giới hạn heap 256 MB, 2 CPU; thực hiện tuần tự. Các bằng chứng Docker ở checkpoint trước chỉ là lịch sử, không phải hướng dẫn tiếp tục.

Ngày lập: 24/11/2026. Design và implementation plan đã được chủ dự án duyệt; đang triển khai. Tasks 01–10 đã được triển khai và review; Tasks 11–34 chưa triển khai. Task 10 và quy trình Java cục bộ được xác minh bằng 63 test không cần database; chưa chạy lại test MySQL trên môi trường cục bộ mới. Bằng chứng tại `docs/verification/2026-11-24-identity-checkpoint.md` và `docs/verification/2026-11-24-development-adapters.md`. Các snippet dưới đây là hướng dẫn, không thay thế source và báo cáo kiểm thử thực tế.

Nguồn: `PROJECT_SPEC.md` và `docs/superpowers/specs/2026-11-24-customer-service-design.md`. Không thay đổi nghiệp vụ đã thống nhất. Không thêm bảng, role, service hoặc framework kiểm thử Frontend.

Workspace lúc lập plan có README, package-lock root rỗng, PROJECT_SPEC và tài liệu. Hiện đã có Backend identity/auth/profile, Frontend scaffold và Compose. Package-lock root được giữ nguyên. Git metadata cần quyền ghi riêng; các bước commit chỉ thực hiện khi có quyền, không dùng việc chưa commit để ngăn hoàn tất tài liệu hoặc kiểm tra cục bộ.

Triển khai trên nhánh `feat/customer-service`, giữ nguyên các file ban đầu của workspace. Đã kiểm tra AGENTS.md (không có), Git status và quyền workspace. Các thay đổi bootstrap/identity được chốt theo checkpoint chung sau review; không đưa .env, cache, artifact hoặc package-lock root vào commit.

Thứ tự phụ thuộc: Tasks 01–04 → 05–09 → 10–17 → 18–24 → 25–30 → 31–34. Các task Frontend có thể làm sau khi Customer API tương ứng ổn định. Task 32 là điều kiện tích hợp thật, không được xem mock đã pass là đã hoàn tất task đó.

Mỗi bước checkbox là một hành động nhỏ, mục tiêu 2–5 phút. Một task có nhiều ca kiểm thử phải lặp RED/GREEN cho từng ca, không viết cả feature trước rồi thêm test. Commit từng task sau verification; chỉ stage đúng file của task, không dùng `git add .`.

## 2. Quy ước đường dẫn và trách nhiệm

Root Java package: `com.dcpost.customer`. Mọi đường dẫn Java dưới đây đều dùng package này. Tên biến/class/method/API identifier bằng tiếng Anh; UI và tài liệu hướng dẫn người dùng bằng tiếng Việt.

| Đường dẫn | Nội dung |
|---|---|
| `customer-service/pom.xml` | Dependency ứng dụng, Java 21, plain JAR và executable JAR classifier `exec`; không dependency test |
| `customer-service/src/main/java/com/dcpost/customer/CustomerApplication.java` | Điểm khởi động |
| `customer-service/src/main/java/com/dcpost/customer/{feature}/` | Controller/service/repository/entity/dto theo feature |
| `customer-service/src/main/java/com/dcpost/customer/integration/{provider}/` | Interface phục vụ nghiệp vụ, RestClient adapter và mapper |
| `customer-service/src/main/resources/application.yml` | Cấu hình chạy thật, tham chiếu biến môi trường |
| `customer-service/src/main/resources/db/migration/` | Migration Flyway, không seed dữ liệu test |
| `customer-web/src/app/` | Router, QueryClient, providers |
| `customer-web/src/features/` | Trang, form, API client và query của từng feature |
| `customer-web/src/layouts/` | PublicLayout và CustomerLayout |
| `customer-web/src/components/ui/` | shadcn/ui |
| `customer-web/src/components/shared/` | Loading/error/empty/pagination/status dùng chung |
| `test/backend/pom.xml` | Module test độc lập phụ thuộc plain JAR Backend |
| `test/backend/src/test/java/com/dcpost/customer/` | Unit/integration test, test configuration và helper |
| `test/backend/src/test/resources/` | Cấu hình MySQL test và fixture |
| `test/mock-server/` | HTTP mock server độc lập và dữ liệu mô phỏng development |
| `test/scripts/` | Script khởi động/chạy/kiểm tra phục vụ test |
| `test/compose.yml` | MySQL test riêng, mock server khi cần |
| `docs/references/` | Ghi chú nguồn tài liệu chính thức và contract đã thống nhất |
| `docs/verification/` | Biên bản kiểm tra thực tế, không chứa fixture hoặc script test |

## 3. Lệnh và nguyên tắc verification

Lệnh chạy từ repository root bằng PowerShell. Dùng `npm.cmd` để không phụ thuộc execution policy của npm.ps1. Không chạy các lệnh dưới đây trong giai đoạn chỉ lập kế hoạch.

```powershell
# Build/install plain JAR và executable JAR, không chạy test trong ứng dụng chính.
mvn -f customer-service/pom.xml clean install

# Một test Backend; thay đúng tên class được ghi trong mỗi task.
mvn -f test/backend/pom.xml '-Dtest=RegistrationServiceTest' test

# Unit test, sau đó integration test trong module độc lập.
mvn -f test/backend/pom.xml test
mvn -f test/backend/pom.xml verify -Pintegration

# Test database dùng MySQL cài trực tiếp, database customer_test, khác customer_dev.
# Chỉ chạy verify -Pintegration khi TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD đã đúng.

# Frontend: kiểm tra TypeScript và build, không thêm frontend test runner.
npm.cmd --prefix customer-web run typecheck
npm.cmd --prefix customer-web run build
```

Sau mọi thay đổi source Backend phải install lại artifact trước khi chạy module test; không chạy test dựa trên JAR cũ. Khi RED cần API chưa tồn tại, tạo signature cần cho nghiệp vụ với implementation tối thiểu chưa đáp ứng hành vi, install artifact rồi chạy test để quan sát assertion thất bại; lỗi compile/setup không được ghi là RED đã đạt. Không thêm test-only API vào source chính.

Test nghiệp vụ/HTTP `*Test` chạy bằng Surefire trên JVM, không cần database hoặc Docker; `DevelopmentAdapterTest` tự bật HTTP server cổng ngẫu nhiên. Test MySQL `*IT` chạy bằng Failsafe trong profile `integration` chỉ ở test/backend. Không tự khởi động database hoặc Docker. Mọi method test có Javadoc và tên `Test_<methodName>_001`, `_002` theo từng method được kiểm thử. Các snippet test sau là phần thân đặt trong class tại đường dẫn đã ghi, với dependency do task đó tạo; phải thêm imports/package đúng khi triển khai.

Mỗi task Backend thực hiện: test mới → chạy thấy đúng failure → minimal implementation → install lại → test pass → refactor nếu cần → chạy lại → review diff → commit nếu quyền cho phép. Chưa chạy thì không ghi pass.

## 4. Hợp đồng nội bộ cần dùng xuyên task

Đây là các chữ ký nội bộ của Customer, không phải contract của service Go. DTO trong domain chỉ chứa dữ liệu Customer cần. Không tạo endpoint/wire field cho API ngoài ở phần này.

```java
public enum PaymentMethod { VNPAY, CASH }
public enum SyncStatus {
    WAITING_PAYMENT, PENDING, PROCESSING, SYNCED, UNKNOWN, BLOCKED, CANCELLED
}
public enum PaymentStatus { PENDING, PAID, FAILED, EXPIRED, UNKNOWN }
public enum RefundStatus { PENDING, PROCESSING, SUCCESS, FAILED }
public enum CancellationStatus { NONE, REQUESTED, UNKNOWN, CONFIRMED, REJECTED }
public enum CancellationSource { LOCAL, EXTERNAL }
```

Không có enum quy trình vận chuyển chính thức. Trạng thái bên ngoài giữ dưới dạng mã nguồn và mapper hiển thị được nhóm thống nhất. Các interface được tạo ở Task 10:

```java
public interface PricingClient {
    FeeQuote quote(FeeRequest request);
}
public interface WarehouseClient {
    List<DropOffLocation> getDropOffLocations();
}
public interface ShippingClient {
    SubmissionResult submit(ShipmentSubmission submission, String requestKey);
    SubmissionLookup findSubmission(String trackingCode);
    CancellationResult cancel(String trackingCode, String requestKey, String reason);
    ShipmentTracking getTracking(String trackingCode);
}
```

Các result phân biệt `CONFIRMED`, `REJECTED`, `UNKNOWN` theo từng thao tác nội bộ; `findSubmission` phải có khả năng biểu diễn `UNSUPPORTED`, không tự diễn giải unsupported thành không có đơn. Các tên operation chỉ thể hiện nhu cầu; nếu contract chưa hỗ trợ thì báo capability không có và giữ trạng thái chưa rõ, không dựng endpoint tưởng tượng.

## Task 01: Xác minh toolchain và ghi phiên bản triển khai

**Files:** Create `docs/references/toolchain.md`; Modify `README.md`.

Đã quan sát: Java/Maven đang dùng JDK 23, Maven 3.9.12; Node 24.14.1, npm 11.11.0; Docker 29.2.1, Compose 5.0.2. Chưa kiểm tra Docker daemon. Java 23 không phải Java 21 đã yêu cầu.

- [x] Chạy `java -version`, `mvn -version`, `node --version`, `npm.cmd --version`, `docker compose version`, `docker info`; ghi đúng kết quả và lỗi nếu có.
- [x] Kiểm tra JDK 21 có sẵn; cấu hình riêng terminal/toolchain cho dự án. Nếu chưa có, báo điều kiện môi trường cần giải quyết; không tự đổi JDK toàn máy.
- [x] Chọn bản vá tương thích thuộc Spring Boot 3.5/Springdoc 2.8, Java 21, MySQL 8.4; xác minh support và dependency BOM từ tài liệu chính thức tại thời điểm thực hiện. Nếu nhánh này không còn phù hợp, trình bày phương án trước khi đổi major version; giữ JUnit 5.
- [x] Ghi chính xác phiên bản đã chọn và URL nguồn trong toolchain.md; pin dependency qua BOM/lockfile, không dùng `latest` trong Docker image.
- [x] Nghiệm thu: Maven thực sự chạy bằng JDK 21, không chỉ đặt compiler release 21 trên JDK 23. Commit `docs: record verified toolchain` sau khi đã xác minh.

## Task 02: Tạo cấu trúc Backend và packaging

**Files:** Create `customer-service/pom.xml`, `customer-service/src/main/java/com/dcpost/customer/CustomerApplication.java`, `customer-service/src/main/resources/application.yml`, `.gitignore`, `.env.example`.

- [x] Tạo Maven project `com.dcpost:customer-service:0.1.0-SNAPSHOT`, Java release 21, dependency runtime theo spec; không có JUnit, Mockito, Starter Test hoặc Surefire/Failsafe cấu hình riêng trong POM chính.
- [x] Cấu hình Boot repackage với classifier `exec`, giữ main artifact là plain JAR:

```xml
<configuration>
  <classifier>exec</classifier>
</configuration>
```

- [x] Khởi tạo entry point thực tế:

```java
package com.dcpost.customer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class CustomerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
```

- [x] application.yml đọc URL/credential MySQL, JWT secret, VNPay secret, API key và endpoint từ môi trường; `ddl-auto=validate`, Flyway bật, Open Session in View tắt, timezone UTC. Không có profile test hoặc fallback giả.
- [x] `.gitignore` bỏ target/node_modules/dist/.env/IDE và log chứa dữ liệu runtime; `.env.example` có tên biến và giá trị minh họa không phải secret thật.
- [x] Chạy `mvn -f customer-service/pom.xml clean install`; kiểm tra plain JAR có class ở package root, exec JAR có BOOT-INF; cả hai không chứa JUnit/test resource. Commit `build: initialize customer backend packaging`.

**Nghiệm thu:** dependency test không có trong dependency tree ứng dụng; không tạo `customer-service/src/test`.

## Task 03: Module test độc lập và MySQL riêng

**Files:** Create `test/backend/pom.xml`, `test/backend/src/test/resources/application-test.yml`, `test/compose.yml`, `test/README.md`, `test/backend/src/test/java/com/dcpost/customer/config/TestRuntimeConfiguration.java`.

- [x] Tạo module chỉ phụ thuộc `customer-service:0.1.0-SNAPSHOT` và test dependencies scope test: JUnit 5, Mockito, Spring Boot test support, MySQL driver cần thiết. Dependency không đi ngược vào POM ứng dụng.
- [x] Surefire chọn `**/*Test.java`; Failsafe profile integration chọn `**/*IT.java`, gắn integration-test/verify. Cả hai fail khi task chọn class nhưng không có test phù hợp.
- [x] Tạo MySQL test port host 3307, database `customer_test`; development sẽ dùng 3306/database khác. Healthcheck và credential test chỉ trong test/.
- [x] Cấu hình Spring integration test trong test module, dùng application-test.yml tại test resources và environment test rõ ràng; không thêm cấu hình test vào application.yml chính.
- [x] Tạo `TestRuntimeConfiguration` nếu cần inject mock dependency ngoài bằng test configuration nằm trong test/; không thay source app để chọn mock. Không dùng Testcontainers.
- [x] Khởi động mysql-test, xác minh database/schema và ghi lệnh build Backend trước khi test vào test/README.md. Chưa có test nghiệp vụ thì không tuyên bố suite pass.

**Nghiệm thu:** test module biên dịch và resolve plain artifact; test DB tách biệt development/production. Commit `test: establish isolated backend test module`.

## Task 04: Tạo Frontend và Compose development

**Files:** Create `customer-web/package.json`, `customer-web/tsconfig.json`, `customer-web/vite.config.ts`, `customer-web/index.html`, `customer-web/src/main.tsx`, `customer-web/src/app/App.tsx`, `customer-web/src/styles/index.css`, `customer-web/Dockerfile`, `customer-service/Dockerfile`, `docker-compose.yml`; Modify `README.md`.

- [x] Khởi tạo React TypeScript bằng Vite trong customer-web, không ghi đè package-lock root. Cài đúng các thư viện Frontend đã chốt, dùng một lockfile của customer-web.
- [x] Khởi tạo Tailwind/shadcn theo phiên bản đã chọn; đặt alias `@` nhất quán TypeScript/Vite. Không cài Vitest/Jest/Playwright/Cypress/Testing Library.
- [x] Thêm scripts:

```json
{
  "dev": "vite",
  "typecheck": "tsc --noEmit",
  "build": "tsc --noEmit && vite build",
  "preview": "vite preview"
}
```

- [x] Compose gồm mysql development, Backend và Frontend; MySQL có volume, healthcheck, database `customer_dev`; Docker build context không đưa test/, .env hoặc secret vào image.
- [x] Docker Backend dùng Java 21 và exec JAR; cấu hình startup dependency healthcheck. Frontend dùng VITE_API_BASE_URL, không nhận secret.
- [x] Chạy typecheck/build; sau migration Task 05 chạy Compose để kiểm tra startup thực tế. Commit `build: initialize customer web and development compose`.

## Task 05: Schema user, role và kiểm tra migration

**Files:** Create `customer-service/src/main/resources/db/migration/V1__create_customer_identity.sql`, `customer-service/src/main/java/com/dcpost/customer/user/entity/User.java`, `customer-service/src/main/java/com/dcpost/customer/user/entity/Role.java`, `customer-service/src/main/java/com/dcpost/customer/user/repository/UserRepository.java`, `customer-service/src/main/java/com/dcpost/customer/user/repository/RoleRepository.java`, `test/backend/src/test/java/com/dcpost/customer/user/IdentitySchemaIT.java`.

- [x] Viết integration test kiểm tra unique email/phone, composite user_roles key, không có FK ngoài database và ROLE CUSTOMER. Tạo fixture bằng SQL trong test resources.
- [x] Chạy install và `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=IdentitySchemaIT'`; RED phải vì thiếu schema/hành vi constraint đã nhắm tới.
- [x] Tạo 3 bảng theo spec; users thêm:

```sql
refresh_token_hash VARCHAR(64) NULL,
refresh_expires_at DATETIME(6) NULL,
auth_version BIGINT NOT NULL DEFAULT 0
```

Ghi chú triển khai: dùng JPA ManyToMany/JoinTable cho user_roles, giữ nguyên composite PK trong migration; không tạo thêm entity UserRole/UserRoleId khi bảng liên kết không có thuộc tính nghiệp vụ riêng.

- [x] Seed duy nhất role CUSTOMER bằng migration; không seed user/password mẫu trong ứng dụng. UserRepository có `findByEmail`, `existsByEmail`, `existsByPhone`, và truy vấn khóa user khi đổi phiên.
- [x] Chạy lại integration test GREEN. Xác minh Flyway chạy lại không nhân đôi role. Commit `feat: create customer identity schema`.

**Nghiệm thu:** mật khẩu chỉ có hash; chuẩn hóa email/phone ở application; constraint là lớp bảo vệ chống race.

## Task 06: Đăng ký bằng TDD

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/auth/service/RegistrationService.java`, `customer-service/src/main/java/com/dcpost/customer/auth/dto/RegisterRequest.java`, `customer-service/src/main/java/com/dcpost/customer/auth/controller/AuthController.java`, `customer-service/src/main/java/com/dcpost/customer/common/exception/ApiExceptionHandler.java`, `test/backend/src/test/java/com/dcpost/customer/auth/RegistrationServiceTest.java`.

- [x] Test `RegistrationService.register(RegisterRequest)` với repository và PasswordEncoder mock tại test module: thành công, email/phone trùng, mật khẩu không đạt validation. Javadoc nói rõ từng hành vi.
- [x] RED cho ca đăng ký thành công: kết quả phải chỉ có CUSTOMER, password lưu khác plaintext và dùng encoder. RED riêng cho duplicate race bị unique constraint chặn.
- [x] Implement DTO validation, chuẩn hóa email bằng `Locale.ROOT`, BCrypt, transaction lưu user + role. Không nhận role đặc quyền từ request.
- [x] Tạo `POST /api/v1/auth/register`; response DTO không có password/refresh hash; global exception dùng Problem Details và field errors; duplicate trả 409.
- [x] Install/test GREEN với `'-Dtest=RegistrationServiceTest'`; integration test mở rộng IdentitySchemaIT cho user_roles nguyên tử. Commit `feat: register customer accounts`.

## Task 07: JWT, phiên đơn và refresh rotation

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/auth/service/AuthService.java`, `customer-service/src/main/java/com/dcpost/customer/security/JwtTokenService.java`, `customer-service/src/main/java/com/dcpost/customer/security/RefreshTokenService.java`, `test/backend/src/test/java/com/dcpost/customer/auth/AuthServiceTest.java`, `test/backend/src/test/java/com/dcpost/customer/security/JwtTokenServiceTest.java`.

- [x] Test login sai mật khẩu, locked account, login mới thay phiên, token expiry 24 giờ; test refresh 7 ngày, xoay hash và token cũ không dùng lại được. Clock được inject cho thời gian nghiệp vụ, không thêm test-only constructor.
- [x] Dùng `JwtTokenService.issue(long userId, long authVersion, Instant issuedAt)` và `verify(String token)`; test chữ ký sai/hết hạn phải bị từ chối, không chỉ test chuỗi decode được.
- [x] Implement login khóa hàng users, tăng auth_version, hash SHA-256 refresh token ngẫu nhiên bằng SecureRandom, lưu expiry. Refresh khóa hàng và thay hash nguyên tử; không tăng auth_version chỉ vì refresh cùng phiên.
- [x] Implement logout/change password thu hồi phiên bằng tăng version và xóa hash/expiry. Không lưu access token trong database, không tạo bảng session.
- [x] Install/test hai class bằng `'-Dtest=AuthServiceTest,JwtTokenServiceTest'`; kiểm tra dùng token của lần login trước bị từ chối sau login mới. Commit `feat: implement single-session jwt authentication`.

**Nghiệm thu:** UI reload có thể refresh; logout vô hiệu hóa access token trước hạn 24 giờ.

## Task 08: HTTP security, CSRF, CORS và API key nội bộ

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/security/SecurityConfiguration.java`, `customer-service/src/main/java/com/dcpost/customer/security/CustomerJwtFilter.java`, `customer-service/src/main/java/com/dcpost/customer/security/InternalApiKeyFilter.java`, `customer-service/src/main/java/com/dcpost/customer/security/RequestRateLimiter.java`, `test/backend/src/test/java/com/dcpost/customer/security/SecurityBoundaryIT.java`, `test/backend/src/test/java/com/dcpost/customer/security/RequestRateLimiterTest.java`; Modify `customer-service/src/main/java/com/dcpost/customer/auth/controller/AuthController.java`.

- [x] RED qua HTTP cho API private không token, token sai/version cũ, CUSTOMER gọi internal, API key không quyền, Origin không cho phép, refresh thiếu CSRF. Token và key trong fixture chỉ ở test/.
- [x] Tách rõ `/api/v1/auth/**`, `/api/v1/public/**`, `/api/v1/customer/**`, `/api/v1/internal/**`, `/api/v1/payments/vnpay/**`. VNPay IPN không dùng JWT/cookie-CSRF nhưng bắt buộc provider signature.
- [x] Login/refresh/logout dùng cookie/Origin/CSRF đúng design; bổ sung endpoint cấp CSRF trước login khi cần. Cookie HttpOnly, SameSite=Lax, Secure theo HTTPS, Path `/api/v1/auth`.
- [x] API key map sang service principal/permissions từ env/config; so sánh an toàn, không log header. JWT filter load trạng thái user/version mỗi request Customer.
- [x] Rate limiter bộ nhớ bounded + TTL, trả 429/Retry-After; không tin X-Forwarded-For từ caller trực tiếp. Test hết hạn bằng Clock, không sleep trong unit test.
- [x] Install/test `SecurityBoundaryIT` và `RequestRateLimiterTest`; log không có token/secret. Commit `feat: enforce customer and internal security boundaries`.

## Task 09: Hồ sơ và đổi mật khẩu

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/user/service/ProfileService.java`, `customer-service/src/main/java/com/dcpost/customer/user/controller/ProfileController.java`, `customer-service/src/main/java/com/dcpost/customer/user/dto/UpdateProfileRequest.java`, `test/backend/src/test/java/com/dcpost/customer/user/ProfileServiceTest.java`.

- [x] RED cho đọc/cập nhật đúng user, phone/email trùng nếu trường được phép đổi, đổi mật khẩu cần mật khẩu cũ, địa chỉ mặc định nullable.
- [x] Implement `getProfile(long userId)`, `updateProfile(long userId, UpdateProfileRequest)`, `changePassword(long userId, String currentPassword, String newPassword)`; không đọc userId từ body làm chủ sở hữu.
- [x] Đổi mật khẩu tăng auth_version và xóa refresh trong cùng transaction; response không chứa hash/version nội bộ.
- [x] Install/test `ProfileServiceTest`; tạo đơn sau này dùng snapshot, không liên kết động với địa chỉ user.
- [x] Commit `feat: manage customer profile and password`.

## Task 10: Interface integration và HTTP mock server độc lập

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/integration/pricing/PricingClient.java`, `customer-service/src/main/java/com/dcpost/customer/integration/warehouse/WarehouseClient.java`, `customer-service/src/main/java/com/dcpost/customer/integration/shipping/ShippingClient.java`, `customer-service/src/main/java/com/dcpost/customer/integration/config/IntegrationProperties.java`, `customer-service/src/main/java/com/dcpost/customer/integration/common/IntegrationException.java`, `test/mock-server/server.py`, `test/mock-server/README.md`, `test/mock-server/fixtures/pricing.json`, `test/mock-server/fixtures/warehouse.json`, `test/mock-server/fixtures/shipping.json`.

- [x] Ghi các chữ ký ở mục 4 và tạo DTO nội bộ trong package `dto` tương ứng: FeeRequest/FeeQuote, DropOffLocation, ShipmentSubmission/SubmissionResult/SubmissionLookup/CancellationResult/ShipmentTracking. Không có wire annotation gắn service Go.
- [x] `FeeQuote` chứa amount VND và thời điểm nhận; shipment tracking tách public event khỏi private data. IntegrationException phân loại connect/read timeout, unavailable, invalid response, unauthorized và unknown outcome.
- [x] Viết HTTP mock server dùng thư viện chuẩn Python, đọc fixture trong test/, có tình huống thành công/timeout/invalid JSON/duplicate; không tạo mock class ở ứng dụng. Route mô phỏng được ghi rõ chỉ là giao thức tạm phục vụ development.
- [x] Thêm lệnh chạy mock server trong test/mock-server/README.md và endpoint env của app. Không tự fallback khi mock hoặc API thật lỗi.
- [x] Tạo HTTP Adapter thực tế tại `customer-service/src/main/java/com/dcpost/customer/integration/pricing/PricingHttpAdapter.java`, `customer-service/src/main/java/com/dcpost/customer/integration/warehouse/WarehouseHttpAdapter.java`, `customer-service/src/main/java/com/dcpost/customer/integration/shipping/ShippingHttpAdapter.java` để ứng dụng development có đủ bean. Giao thức HTTP tạm chỉ dùng với mock server; endpoint lấy từ cấu hình, không fake response trong adapter. Test mapping/timeout tại `test/backend/src/test/java/com/dcpost/customer/integration/DevelopmentAdapterTest.java` theo TDD trước khi thêm xử lý HTTP; Task 32 cập nhật sang contract thật. Tên Test thay IT để chạy mặc định trên JVM theo yêu cầu bỏ Docker; không cần database.
- [x] Kiểm tra bằng `python test/mock-server/server.py --help`, sau đó HTTP request tới mock bằng `Invoke-WebRequest`; lệnh này chỉ kiểm tra mock server, không xác nhận API Go. Commit `test: define isolated development integration mocks`.

**Nghiệm thu:** source chính không chứa fake data/mock bean; không có tài liệu nào gọi route mô phỏng là contract đã chốt.

## Task 11: Schema vận đơn, hàng hóa và tracking code

**Files:** Create `customer-service/src/main/resources/db/migration/V2__create_shipments.sql`, `customer-service/src/main/java/com/dcpost/customer/shipment/entity/Shipment.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/entity/ShipmentItem.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/TrackingCodeGenerator.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentSchemaIT.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/TrackingCodeGeneratorTest.java`.

- [ ] RED cho customer NULL hợp lệ ở COUNTER, ONLINE phải có customer, không COUNTER/HOME_PICKUP, trọng lượng/kích thước > 0, quantity > 0, declared_value >= 0 và tracking unique.
- [ ] Tạo hai bảng theo spec, thêm đúng nhóm trường trong design; creation_request_scope/key có unique chung, FK chỉ tới bảng local. Các CHECK constraint phải thực sự được MySQL thực thi.
- [ ] Lưu payment_method ngay trên shipment; status đồng bộ/hủy riêng. Dùng tracking code ngẫu nhiên đủ khó đoán, ví dụ tiền tố DC + 128 bit random biểu diễn hex; không lộ shipment_id tuần tự.
- [ ] Index `(customer_id, created_at, shipment_id)`, `(sync_status, sync_next_attempt_at)` và lịch hủy; chỉ thêm index recipient khi truy vấn thực tế hưởng lợi.
- [ ] Test code generation không mang tính probabilistic flaky: kiểm tra format và xử lý unique conflict bằng repository, không chứng minh uniqueness bằng sinh vài chuỗi.
- [ ] Install/test hai class; thông báo nếu external warehouse/actor ID không tương thích BIGINT trước khi đổi schema. Commit `feat: persist shipment snapshots and items`.

## Task 12: Tạo đơn, phí và idempotency

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentCreationService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentWriteService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/repository/ShipmentRepository.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/dto/CreateShipmentRequest.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentCreationServiceTest.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentIdempotencyIT.java`.

- [ ] RED từng ca: ONLINE HOME_PICKUP/CASH, ONLINE DROP_OFF/VNPAY, nhiều mặt hàng, thiếu bưu cục, phí frontend bị sửa, pricing unavailable, cùng key cùng payload, cùng key khác payload.
- [ ] `ShipmentCreationService.create(CustomerPrincipal, String requestKey, CreateShipmentRequest)` gọi Pricing/Warehouse ngoài transaction; nhận phí mới khác phí khách xác nhận thì trả conflict yêu cầu xác nhận lại, không lưu đơn.
- [ ] `ShipmentWriteService` là bean riêng nhận dữ liệu đã xác minh và transaction ngắn; tránh tự gọi method @Transactional cùng bean. Canonical hash chỉ dữ liệu nghiệp vụ đã chuẩn hóa, không timestamp ngẫu nhiên.
- [ ] Trước khi gọi lại dependency, ưu tiên tìm bản ghi theo idempotency scope/key để replay yêu cầu đã commit. Race unique constraint được xử lý ngoài transaction đã rollback, sau đó đọc bản ghi thắng và đối chiếu hash.
- [ ] Lưu snapshot/items/phí/phương thức nguyên tử; CASH=PENDING, VNPAY=WAITING_PAYMENT. Không gọi ShippingClient ở request tạo đơn.
- [ ] Install/test unit và integration; test transaction rollback không có item mồ côi. Commit `feat: create idempotent shipments with verified fees`.

**Nghiệm thu:** không lưu shipment trùng khi hai request đồng thời cùng key; DB transaction không mở trong Pricing/Warehouse HTTP.

## Task 13: Counter API và CASH permission

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/shipment/controller/InternalShipmentController.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/dto/CounterShipmentRequest.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/CounterShipmentIT.java`; Modify `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentCreationService.java`.

- [ ] RED cho COUNTER/DROP_OFF không customer, COUNTER/HOME_PICKUP bị chặn, CUSTOMER token không được tạo hộ, actor source được lưu đúng service principal.
- [ ] Tạo `POST /api/v1/internal/shipments`; service principal có permission tạo counter mới được gọi. `created_by` là nhân viên từ service được tin cậy, kèm nguồn; không tạo users cho guest.
- [ ] Counter có customer_id chỉ được liên kết nếu phía gọi cung cấp bằng chứng/định danh theo contract nhóm. Khi chưa có cơ chế đó, chấp nhận counter không customer, không tự ghép theo phone/email.
- [ ] Install/test `CounterShipmentIT`; kiểm tra scope idempotency giữa hai service không trùng nhau.
- [ ] Commit `feat: expose authenticated counter shipment creation`.

## Task 14: Danh sách, chi tiết và quyền sở hữu

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentQueryService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/controller/ShipmentController.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/dto/ShipmentDetailResponse.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentOwnershipIT.java`.

- [ ] RED với hai customer: list chỉ chứa đơn mình, ID của người khác trả 404, search recipient chỉ trong customer scope, phân trang/sort ổn định khi thời gian trùng.
- [ ] Tạo `GET /api/v1/customer/shipments`, `GET /api/v1/customer/shipments/{id}` và `POST /api/v1/customer/shipments`; không PUT/PATCH/DELETE shipment.
- [ ] Query pagination giới hạn page size, sort created_at/shipment_id; lọc payment method/status bằng dữ liệu local. Detail tổng hợp phần bên ngoài riêng: API lỗi không làm mất dữ liệu gốc đã đọc.
- [ ] Nếu trạng thái ngoài không có batch/filter capability thì không bật bộ lọc đó; không lọc sau khi phân trang local. Trả capability rõ ràng cho Frontend.
- [ ] Install/test `ShipmentOwnershipIT`; kiểm tra request không thể override customer_id. Commit `feat: query shipments within customer ownership`.

## Task 15: Scheduler gửi đơn và atomic claim

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentSyncScheduler.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentSyncService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentSyncStateService.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentSyncServiceTest.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentSyncClaimIT.java`.

- [ ] RED: CASH được gửi, VNPAY chưa PAID không gửi, hai worker chỉ một HTTP call, đơn đã hủy không gửi. Test concurrency dùng latch/barrier, không sleep để đoán thứ tự.
- [ ] Atomic claim đặt PROCESSING/start time/increment attempt trong transaction; điều kiện phải gồm PENDING, đến hạn, chưa hủy/chưa yêu cầu hủy, payment hợp lệ. Kiểm tra số hàng cập nhật đúng 1 trước HTTP.
- [ ] Dùng một câu lệnh update có điều kiện tương đương:

```sql
UPDATE shipments
SET sync_status = 'PROCESSING', sync_started_at = :now,
    sync_attempt_count = sync_attempt_count + 1
WHERE shipment_id = :id AND sync_status = 'PENDING'
  AND cancellation_status = 'NONE'
  AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now);
```

- [ ] Điều kiện CASH/PAID được kiểm tra cùng lock shipment và điều kiện claim trước commit; không dùng snippet SQL một mình thay cho kiểm tra tài chính.
- [ ] Commit claim rồi gọi ShippingClient; kết quả ghi bằng transaction mới. Worker result có điều kiện attempt/start time để response cũ không ghi đè kết quả đối soát mới.
- [ ] Install/test hai class. Xác minh bằng interceptor test rằng HTTP ngoài transaction, không thêm hook vào app. Commit `feat: synchronize eligible shipments with atomic claims`.

## Task 16: Timeout, restart và retry an toàn

**Files:** Modify `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentSyncService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentSyncStateService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentSyncScheduler.java`; Create `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentRecoveryIT.java`.

- [ ] RED cho connect failure chắc chắn chưa gửi, read timeout sau tiếp nhận, response sai cấu trúc, auth failure và PROCESSING bị gián đoạn.
- [ ] Chuyển tình huống có thể đã gửi sang UNKNOWN; PROCESSING quá hạn cũng UNKNOWN, không tự PENDING. Không lấy HTTP 500 làm bằng chứng chắc chắn bên ngoài chưa lưu.
- [ ] `findSubmission` xác nhận đã tiếp nhận thì SYNCED; xác nhận chưa nhận chỉ được retry nếu contract bảo đảm kết luận đó đủ an toàn; UNSUPPORTED giữ UNKNOWN/BLOCKED với mã lý do.
- [ ] Backoff cấu hình, lô nhỏ và giới hạn attempt; không giữ khóa khi sleep/HTTP. Ghi code lỗi đã làm sạch, không payload cá nhân.
- [ ] Install/test `ShipmentRecoveryIT`; mô phỏng restart bằng persisted state và khởi tạo lại service/scheduler test, không cần kill ứng dụng chính. Commit `fix: recover uncertain shipment submissions safely`.

## Task 17: Customer API documentation và error contract

**Files:** Create `docs/customer-api.md`, `customer-service/src/main/java/com/dcpost/customer/common/dto/PageResponse.java`, `customer-service/src/main/java/com/dcpost/customer/common/exception/BusinessException.java`, `test/backend/src/test/java/com/dcpost/customer/common/ErrorResponseIT.java`; Modify `customer-service/src/main/java/com/dcpost/customer/common/exception/ApiExceptionHandler.java`.

- [ ] RED cho validation 400, unauthenticated 401, internal permission 403, private missing 404, key mismatch/fee changed 409, rate limit 429 và dependency unavailable 503.
- [ ] Trả Problem Details có code/correlationId/fieldErrors, không stack trace/SQL/secret. Thành công trả DTO/resource bình thường, page có items/page/size/totalElements/totalPages.
- [ ] Ghi API Customer trong docs và Springdoc; các API Go chưa có contract không xuất bản từ app như API sở hữu của Customer.
- [ ] Install/test `ErrorResponseIT`; kiểm tra OpenAPI chỉ mô tả đúng endpoint Customer và phân nhóm auth/internal/public.
- [ ] Commit `docs: define customer api and error responses`.

## Task 18: Xác minh VNPay trước migration tài chính

**Files:** Create `docs/references/vnpay-sandbox.md`, `docs/references/integration-readiness.md`; Modify design mục thông tin provider sau khi đối chiếu nguồn.

- [ ] Đọc tài liệu chính thức `https://sandbox.vnpayment.vn/apis/docs/`; ghi phiên bản API, canonicalization/signature, VND scaling, timezone, callback acknowledgement, query và refund response semantics.
- [ ] Kiểm tra merchant Sandbox có quyền payment/query/refund, IPN URL được VNPay truy cập và cách xác minh hoàn tiền cuối cùng. Không đưa credential vào tài liệu/log.
- [ ] Chốt trường lưu từ giao dịch gốc phục vụ query/refund và quy tắc request ID. Nếu query không đủ xác nhận refund thì ghi rõ giới hạn và giữ PROCESSING khi chưa rõ.
- [ ] Chưa có merchant vẫn tiếp tục unit/adapter test bằng test fixture; đánh dấu smoke Sandbox chưa chạy, không thay bằng giả success.
- [ ] Nghiệm thu: có nguồn và mapping xác minh, không suy đoán tài liệu provider. Commit `docs: verify vnpay payment and refund requirements`.

## Task 19: Schema payment/refund và cạnh tranh attempt

**Files:** Create `customer-service/src/main/resources/db/migration/V3__create_payments_and_refunds.sql`, `customer-service/src/main/java/com/dcpost/customer/payment/entity/Payment.java`, `customer-service/src/main/java/com/dcpost/customer/payment/repository/PaymentRepository.java`, `customer-service/src/main/java/com/dcpost/customer/refund/entity/Refund.java`, `customer-service/src/main/java/com/dcpost/customer/refund/repository/RefundRepository.java`, `test/backend/src/test/java/com/dcpost/customer/payment/FinancialSchemaIT.java`.

- [ ] RED cho transaction_ref unique, refund.payment_id unique, amount đúng precision, không xóa lịch sử, state lưu đúng enum local.
- [ ] Tạo 2 bảng theo spec và đúng trường provider đã xác minh ở Task 18; thêm expires_at/next_check_at cần cho đối soát. Không thêm bảng nghiệp vụ mới.
- [ ] Nếu cần lưu nhiều request refund kỹ thuật, lưu metadata JSON nhỏ trên refunds như design: requestId/time/resultCode, không toàn bộ PII payload. Không ghi đè ID cũ trước khi đã lưu dấu vết đối soát.
- [ ] PaymentRepository có query kiểm tra attempt PENDING/UNKNOWN và PAID dưới lock shipment; RefundRepository có atomic claim PENDING → PROCESSING.
- [ ] Install/test `FinancialSchemaIT`; query information_schema xác minh chỉ có 7 bảng nghiệp vụ và bảng metadata Flyway. Commit `feat: persist payment and refund records`.

## Task 20: VNPay signing và tạo payment attempt

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/integration/vnpay/VnPayClient.java`, `customer-service/src/main/java/com/dcpost/customer/integration/vnpay/VnPayHttpAdapter.java`, `customer-service/src/main/java/com/dcpost/customer/integration/vnpay/VnPaySigner.java`, `customer-service/src/main/java/com/dcpost/customer/payment/service/PaymentService.java`, `customer-service/src/main/java/com/dcpost/customer/payment/controller/PaymentController.java`, `test/backend/src/test/java/com/dcpost/customer/payment/PaymentServiceTest.java`, `test/backend/src/test/java/com/dcpost/customer/integration/vnpay/VnPaySignerTest.java`.

- [ ] RED bằng vector chữ ký/encoding lấy từ tài liệu provider, gồm ký tự tiếng Việt, tham số rỗng, amount scale và sai chữ ký. Không tự dùng chính implementation tạo expected signature.
- [ ] `PaymentService.createVnPayPayment(long customerId, long shipmentId)` khóa shipment, kiểm tra chủ sở hữu/phương thức/chưa hủy/chưa PAID/chưa có attempt chưa rõ; lưu reference duy nhất và amount từ shipping_fee.
- [ ] Xây signed URL theo protocol chính thức; không lấy amount/merchant/return target tùy ý từ Frontend. Không giữ transaction trong HTTP nếu thao tác provider cần network.
- [ ] `POST /api/v1/customer/shipments/{id}/payments/vnpay` trả paymentId/paymentUrl/expiry; không có secret. Retry request khi attempt đang PENDING trả cùng attempt an toàn thay vì tạo lần thu mới.
- [ ] Install/test `PaymentServiceTest,VnPaySignerTest`. Commit `feat: create verified vnpay payment attempts`.

## Task 21: IPN, Return URL và payment reconciliation

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/payment/service/PaymentNotificationService.java`, `customer-service/src/main/java/com/dcpost/customer/payment/service/PaymentReconciliationScheduler.java`, `customer-service/src/main/java/com/dcpost/customer/payment/controller/VnPayCallbackController.java`, `test/backend/src/test/java/com/dcpost/customer/payment/PaymentNotificationIT.java`, `test/backend/src/test/java/com/dcpost/customer/payment/PaymentReconciliationTest.java`.

- [ ] RED cho chữ ký sai, ref/merchant/amount sai, duplicate callback, success rồi failure cũ, UNKNOWN query lại, Return URL giả success không ghi PAID.
- [ ] IPN xác minh bằng signer rồi khóa shipment/payment theo thứ tự nhất quán, cập nhật PAID và WAITING_PAYMENT → PENDING nguyên tử; nếu cancellation CONFIRMED thì không gửi và chuyển xử lý refund Task 24.
- [ ] Response IPN đúng acknowledgement provider Task 18, phân biệt không hợp lệ và đã xử lý. Return handler chỉ redirect tới `/dashboard/payments/result` theo allowlist.
- [ ] Scheduler query attempt chưa rõ, xác minh response rồi gọi cùng nghiệp vụ cập nhật như IPN; không mark EXPIRED từ đồng hồ local khi vẫn có khả năng đã thu.
- [ ] Install/test hai class; two concurrent callbacks không tạo side effect trùng. Commit `feat: verify vnpay callbacks and reconcile payment results`.

## Task 22: Xác nhận CASH và lịch sử giao dịch

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/payment/service/CashPaymentService.java`, `customer-service/src/main/java/com/dcpost/customer/payment/controller/InternalPaymentController.java`, `customer-service/src/main/java/com/dcpost/customer/payment/service/PaymentQueryService.java`, `test/backend/src/test/java/com/dcpost/customer/payment/CashPaymentIT.java`, `test/backend/src/test/java/com/dcpost/customer/payment/PaymentOwnershipIT.java`.

- [ ] RED cho internal key sai/quyền thiếu, CUSTOMER tự xác nhận, biên nhận trùng, số tiền sai, đơn VNPAY nhận CASH sai phương thức và lịch sử của customer khác.
- [ ] `POST /api/v1/internal/shipments/{id}/cash-confirmations` yêu cầu principal có permission và external receipt reference; số tiền phải khớp phí backend, lock shipment chống ghi PAID hai lần.
- [ ] Cùng receipt trả kết quả cũ; receipt khác khi đơn đã PAID không tạo giao dịch thu mới. Nếu thông báo thu tiền thực tế tới sau hủy, giữ thông tin để phối hợp thay vì hoàn qua VNPay.
- [ ] Tạo GET payment list/detail theo customer ownership qua shipment; expose đủ dữ liệu cho trang kết quả query sau Return URL.
- [ ] Install/test hai class; không thêm UI thu tiền. Commit `feat: confirm cash payments through trusted internal api`.

## Task 23: Hủy local/external và tránh race với scheduler

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentCancellationService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/CancellationReconciliationScheduler.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/dto/CancelShipmentRequest.java`, `test/backend/src/test/java/com/dcpost/customer/shipment/ShipmentCancellationIT.java`; Modify `customer-service/src/main/java/com/dcpost/customer/shipment/controller/ShipmentController.java`.

- [ ] RED riêng cho local WAITING_PAYMENT/PENDING chưa từng claim, đơn không thuộc mình, PROCESSING/UNKNOWN không hủy local, external reject/timeout, yêu cầu hủy lặp.
- [ ] `POST /api/v1/customer/shipments/{id}/cancellations` nhận lý do và idempotency key. Lock shipment; local chỉ khi chưa từng attempt, chưa worker claim; set cancellation CONFIRMED/LOCAL và sync CANCELLED nguyên tử.
- [ ] Đã SYNCED thì ghi REQUESTED/key trước HTTP, gọi outside transaction, rồi ghi kết quả; UNKNOWN phải đối soát. Không dùng trạng thái vận chuyển tự định nghĩa để quyết định điều kiện hủy.
- [ ] Scheduler không claim đơn đang REQUESTED/UNKNOWN/CONFIRMED cancellation. Nếu external từ chối, phục hồi khả năng xử lý theo sync state đã xác minh, không tự mất đơn.
- [ ] Install/test `ShipmentCancellationIT`, gồm latch cho cancel vs claim và cancel vs PAID callback; không refund trước CONFIRMED. Commit `feat: cancel shipments safely before or after submission`.

## Task 24: Hoàn tiền toàn phần, claim và đối soát

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/refund/service/RefundService.java`, `customer-service/src/main/java/com/dcpost/customer/refund/service/RefundScheduler.java`, `customer-service/src/main/java/com/dcpost/customer/refund/controller/RefundController.java`, `test/backend/src/test/java/com/dcpost/customer/refund/RefundServiceTest.java`, `test/backend/src/test/java/com/dcpost/customer/refund/RefundConcurrencyIT.java`, `test/backend/src/test/java/com/dcpost/customer/refund/RefundOwnershipIT.java`; Modify `customer-service/src/main/java/com/dcpost/customer/payment/service/PaymentNotificationService.java`, `customer-service/src/main/java/com/dcpost/customer/shipment/service/ShipmentCancellationService.java`.

- [ ] RED cho PAID + hủy CONFIRMED tạo một refund, chưa PAID/chưa hủy/CASH không gọi VNPay, nhiều callback/hủy đồng thời không tạo bản ghi thứ hai.
- [ ] Hai đường PAID sau hủy và hủy sau PAID cùng gọi `RefundService.ensureRefund(long paymentId)` trong transaction; unique payment_id bảo vệ. Không gọi HTTP trong transaction đó.
- [ ] Scheduler claim PENDING → PROCESSING nguyên tử rồi gọi refund. Response chỉ tiếp nhận giữ PROCESSING; confirmed final success mới SUCCESS; failure xác định mới FAILED.
- [ ] Timeout/restart giữ PROCESSING, lưu next_check; chỉ retry sau đối soát theo capability provider. Không gửi lại mù quáng khi không có API kiểm tra kết quả.
- [ ] Refund FAILED không thay PAID/cancellation confirmation; GET `/api/v1/customer/refunds` và detail kiểm tra owner qua payment/shipment, không tin userId từ query.
- [ ] Install/test ba class; test hai worker chỉ một refund HTTP call và callback PAID tới sau local cancel không gửi shipment. Commit `feat: refund cancelled vnpay shipments without duplicates`.

## Task 25: Tracking công khai và privacy

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/tracking/service/TrackingService.java`, `customer-service/src/main/java/com/dcpost/customer/tracking/controller/PublicTrackingController.java`, `customer-service/src/main/java/com/dcpost/customer/tracking/dto/PublicTrackingResponse.java`, `test/backend/src/test/java/com/dcpost/customer/tracking/PublicTrackingIT.java`.

- [ ] RED kiểm tra serialized JSON không chứa phone/address/payment/refund/employee/GPS dù payload bên ngoài có các field đó; code không tồn tại và API lỗi có kết quả khác nhau.
- [ ] `GET /api/v1/public/tracking/{trackingCode}` validate format, rate limit, lấy dữ liệu ngoài qua ShippingClient và chỉ map allowlist public.
- [ ] Đơn chưa gửi không tạo timeline giả; hiển thị thông báo chưa có dữ liệu vận chuyển. Không tiết lộ chờ thanh toán hoặc dữ liệu tài chính qua public tracking.
- [ ] Install/test `PublicTrackingIT`, gồm limit/429, mã khó đoán và payload không hợp lệ.
- [ ] Commit `feat: expose privacy-preserving public shipment tracking`.

## Task 26: Dashboard tổng hợp đúng dữ liệu

**Files:** Create `customer-service/src/main/java/com/dcpost/customer/dashboard/service/DashboardService.java`, `customer-service/src/main/java/com/dcpost/customer/dashboard/controller/DashboardController.java`, `test/backend/src/test/java/com/dcpost/customer/dashboard/DashboardServiceTest.java`, `test/backend/src/test/java/com/dcpost/customer/dashboard/DashboardOwnershipIT.java`.

- [ ] RED cho tổng đơn/biểu đồ/payment chỉ của customer, ranh giới ngày Asia/Ho_Chi_Minh, payment retry không làm cộng tiền nhiều lần và refund hiển thị riêng.
- [ ] `GET /api/v1/customer/dashboard` nhận khoảng ngày có giới hạn; chuyển sang UTC để query. Tổng phí đã thanh toán là tổng giao dịch PAID thực tế, không coi refund là payment FAILED.
- [ ] Số lượng theo trạng thái vận chuyển chỉ có khi API đủ capability/mapping; thiếu dữ liệu trả availability=false và giá trị nullable, không 0 giả.
- [ ] Install/test hai class; không gọi HTTP từng đơn không giới hạn để xây dashboard.
- [ ] Commit `feat: aggregate customer dashboard with availability indicators`.

## Task 27: Frontend nền tảng, auth và layouts

**Files:** Create `customer-web/src/app/router.tsx`, `customer-web/src/app/queryClient.ts`, `customer-web/src/lib/http.ts`, `customer-web/src/features/auth/AuthProvider.tsx`, `customer-web/src/features/auth/RequireAuth.tsx`, `customer-web/src/features/auth/LoginPage.tsx`, `customer-web/src/features/auth/RegisterPage.tsx`, `customer-web/src/layouts/PublicLayout.tsx`, `customer-web/src/layouts/CustomerLayout.tsx`, `customer-web/src/components/shared/AsyncState.tsx`; Create `test/manual/frontend-checklist.md`.

- [ ] Dựng PublicLayout và CustomerLayout với đúng navigation trong spec; sidebar responsive, account menu, loading/error/empty component dùng chung. Không có menu Admin/Shipper.
- [ ] http.ts lưu access token trong closure/module memory; Axios `withCredentials` cho refresh theo origin cấu hình. Thực hiện refresh single-flight, giới hạn một lần retry; lỗi auth xóa Query cache.
- [ ] AuthProvider bootstrap CSRF/refresh; RequireAuth chờ bootstrap rồi chuyển login với return path cùng website đã validate. Không lưu token trong localStorage/sessionStorage.
- [ ] Login/Register dùng RHF/Zod, role không xuất hiện trong form; hiển thị lỗi backend theo field. Logout xóa token/query và cookie qua backend.
- [ ] Typecheck/build GREEN; kiểm tra thủ công login/reload/expiry/logout/login mới, route guard và responsive ở 360/768/1280px. Ghi bằng chứng trong docs/verification khi đã chạy.
- [ ] Commit `feat: build customer authentication and shared layouts`.

**Nghiệm thu:** UI không flash dữ liệu customer cũ khi đổi tài khoản; backend vẫn là nơi bảo vệ quyền.

## Task 28: Public website, tracking và profile UI

**Files:** Create `customer-web/src/features/home/HomePage.tsx`, `customer-web/src/features/tracking/TrackingPage.tsx`, `customer-web/src/features/tracking/TrackingResultPage.tsx`, `customer-web/src/features/tracking/api.ts`, `customer-web/src/features/profile/ProfilePage.tsx`, `customer-web/src/features/profile/ChangePasswordPage.tsx`; Modify `customer-web/src/app/router.tsx`, `test/manual/frontend-checklist.md`.

- [ ] Home gồm hero, tra cứu nổi bật, hai dịch vụ, hướng dẫn gửi hàng, CTA và footer; nội dung tĩnh không phát sinh backend mới.
- [ ] Tracking route theo spec; timeline chỉ sự kiện có thật, không giả vị trí/GPS. Polling có interval và dừng/background handling theo TanStack Query, lỗi cập nhật không đổi trạng thái vận chuyển.
- [ ] Profile cho xem/sửa thông tin và địa chỉ mặc định; đổi mật khẩu yêu cầu mật khẩu cũ và đăng nhập lại khi backend thu hồi phiên.
- [ ] Kiểm tra thủ công mã đúng/sai/service lỗi/429, Guest CTA về login, không lộ PII, field errors và responsive.
- [ ] Typecheck/build; commit `feat: build public tracking and customer profile pages`.

## Task 29: UI tạo đơn và quản lý vận đơn

**Files:** Create `customer-web/src/features/shipments/CreateShipmentPage.tsx`, `customer-web/src/features/shipments/ShipmentListPage.tsx`, `customer-web/src/features/shipments/ShipmentDetailPage.tsx`, `customer-web/src/features/shipments/schema.ts`, `customer-web/src/features/shipments/api.ts`, `customer-web/src/features/shipments/components/ShipmentItemsFieldArray.tsx`, `customer-web/src/features/shipments/components/ShipmentProgress.tsx`; Modify `customer-web/src/app/router.tsx`, `test/manual/frontend-checklist.md`.

- [ ] Form 4 bước dùng RHF/FormProvider và Zod, sender mặc định từ profile, HOME_PICKUP/DROP_OFF conditional, field array nhiều mặt hàng. Không biến hàng khai báo thành Product/COD.
- [ ] Khi input ảnh hưởng phí đổi, vô hiệu phí cũ; lấy fee/backend và yêu cầu xác nhận lại khi 409 fee changed. Giữ form khi quay bước, reset sau tạo thành công.
- [ ] Tạo idempotency key bằng `crypto.randomUUID()` cho lần xác nhận; retry cùng payload giữ key, không đổi key chỉ vì timeout. Nút submit disable tránh double click nhưng không thay thế backend idempotency.
- [ ] List có search/filter local/pagination/sort; filter trạng thái chỉ bật khi capability cho phép. Detail hiển thị snapshot, items, payment/refund và dữ liệu vận chuyển riêng.
- [ ] Hiển thị `Đang xử lý — Đã thanh toán, đang đồng bộ vận đơn` khi PAID nhưng chưa SYNCED; không hiện nút trả tiền lại trong trường hợp đó.
- [ ] Hủy bằng confirmation dialog + reason; REQUESTED/UNKNOWN hiển thị đang xử lý, không tự đánh dấu thành công hoặc thêm nút sửa/xóa.
- [ ] Kiểm tra thủ công HOME_PICKUP/VNPAY, DROP_OFF/CASH, pricing lỗi, fee changed, submit timeout/retry, hủy local/external/unknown. Typecheck/build rồi commit `feat: build shipment creation and management flows`.

## Task 30: UI thanh toán, hoàn tiền và dashboard

**Files:** Create `customer-web/src/features/payments/PaymentHistoryPage.tsx`, `customer-web/src/features/payments/PaymentResultPage.tsx`, `customer-web/src/features/payments/api.ts`, `customer-web/src/features/refunds/RefundHistoryPage.tsx`, `customer-web/src/features/refunds/api.ts`, `customer-web/src/features/dashboard/DashboardPage.tsx`, `customer-web/src/features/dashboard/api.ts`; Modify `customer-web/src/app/router.tsx`, `test/manual/frontend-checklist.md`.

- [ ] Nút VNPay gọi backend lấy URL, thông báo Sandbox rõ ràng rồi redirect. Payment result đọc API xác thực, không dùng query string kết luận PAID.
- [ ] Polling payment đang chờ/chưa rõ có giới hạn; không hiện thanh toán lại khi chưa kết luận. Chỉ cho retry nếu backend báo đủ điều kiện.
- [ ] History payment/refund đúng customer; hoàn tiền PROCESSING hiển thị đang xử lý, FAILED chỉ ở refund. Không tạo màn hình thu tiền CASH.
- [ ] Dashboard thẻ thống kê, Recharts theo thời gian, recent shipments và quick-create. Dữ liệu unavailable hiển thị thông báo, không hardcode/0 thay thế.
- [ ] Kiểm tra thủ công Return giả success, reload result, callback chậm, PAID nhưng đồng bộ lỗi, refund timeout, empty/loading/error và responsive. Typecheck/build; commit `feat: build customer finance and dashboard pages`.

## Task 31: Kiểm thử tích hợp và cạnh tranh tổng thể

**Files:** Create `test/backend/src/test/java/com/dcpost/customer/scenario/CustomerAcceptanceIT.java`, `test/backend/src/test/java/com/dcpost/customer/scenario/FinancialRaceIT.java`, `test/backend/src/test/resources/scenarios/customer-fixtures.sql`, `test/scripts/verify-isolation.ps1`, `docs/verification/backend-acceptance.md`.

- [ ] Viết acceptance integration test cho 9 scenario gốc, thêm local cancel trước gửi; fixture chỉ trong test/. Dependency ngoài mock qua test configuration hoặc mock server.
- [ ] Tái hiện race bằng hai transaction/HTTP request và latch: cùng key tạo đơn, mở payment hai lần, duplicate IPN, cancel vs sync claim, PAID sau cancel, hai refund worker.
- [ ] Chạy unit suite rồi `verify -Pintegration` trên MySQL test. Mọi lỗi phải thêm/giữ ca RED tái hiện trước sửa source thật; không thêm bypass vào app.
- [ ] Script isolation kiểm tra không có test source/dependency/fixture/mock ở customer-service và customer-web; kiểm tra artifact/image không mang test resources.
- [ ] Lưu số test/thất bại và lệnh thực tế, phân biệt mock integration và provider smoke. Commit `test: verify customer acceptance and financial concurrency`.

**Nghiệm thu:** tất cả ca thuộc phạm vi chạy có kết quả thực tế; không bỏ test bằng skip để ghi pass.

## Task 32: Tích hợp API thật theo contract nhóm

**Files:** Create `docs/references/shipping-contract.md`, `docs/references/pricing-contract.md`, `docs/references/warehouse-contract.md`, `test/backend/src/test/java/com/dcpost/customer/integration/ExternalAdapterContractIT.java`; Modify `customer-service/src/main/java/com/dcpost/customer/integration/shipping/ShippingHttpAdapter.java`, `customer-service/src/main/java/com/dcpost/customer/integration/pricing/PricingHttpAdapter.java`, `customer-service/src/main/java/com/dcpost/customer/integration/warehouse/WarehouseHttpAdapter.java`, `docs/references/integration-readiness.md`.

- [ ] Thu thập contract do nhóm xác nhận: endpoint/service owner/auth/ID/field/timeout/idempotency/lookup/cancel/public data/batch/filter. Không tự điền phần chưa có và gọi là đã thống nhất.
- [ ] Đối chiếu HTTP adapter đã được Task 10 tạo cho development với contract thật. Tài liệu ghi rõ protocol tạm chưa phải contract; không coi Task 32 hoàn tất chỉ vì mock đã chạy. Không đưa fake implementation vào main.
- [ ] RED bằng response fixture theo contract thật, sau đó cập nhật wire DTO/mapper/HTTP adapter; nghiệp vụ Customer không phụ thuộc field JSON của Go.
- [ ] Kiểm tra auth failure, invalid payload, timeout và duplicate với phía nhận; xác nhận việc chỉ gửi VNPAY sau PAID. Chưa có lookup/idempotency thì giữ UNKNOWN, không bật retry nguy hiểm.
- [ ] Chạy contract test và smoke liên service ở môi trường đã thống nhất. Không gửi tin nhắn cho thành viên khác qua công cụ khi chưa được user yêu cầu; user cung cấp/thống nhất contract qua quy trình nhóm.
- [ ] Ghi capability thực tế, bật filter/statistics chỉ khi đủ dữ liệu. Commit `feat: integrate verified external service contracts`.

**Nghiệm thu:** contract test pass không thay thế việc xác nhận kết nối thật; nếu service chưa sẵn sàng, ghi task chưa hoàn tất và tiếp tục các task độc lập.

## Task 33: VNPay Sandbox smoke và security review

**Files:** Create `test/manual/vnpay-sandbox-checklist.md`, `docs/verification/vnpay-sandbox.md`, `docs/verification/security-review.md`; Modify `test/manual/frontend-checklist.md`.

- [ ] Thực hiện flow Sandbox thành công/thất bại/return mất phiên/IPN lặp; đối chiếu payment ref và tiền backend, không ghi secret/PII vào biên bản.
- [ ] Hủy external/local sau PAID và thử refund Sandbox khi merchant hỗ trợ; timeout giữ PROCESSING và đối soát thực tế. Nếu refund chưa được cấp quyền, ghi rõ chưa verify được, không tuyên bố hoàn tất refund thật.
- [ ] Review ownership mọi endpoint; chống giả cash, public payload allowlist, secrets trong repo/image, log header/body, cookie/CORS/CSRF và JWT version revocation.
- [ ] Review source không giữ transaction qua HTTP, không mock fallback và không cập nhật trạng thái vận chuyển chính thức.
- [ ] Sửa lỗi bằng TDD Backend, chạy lại đúng ca liên quan; chỉ mở rộng suite khi thay đổi cần. Commit `docs: record sandbox and security verification`.

## Task 34: README, chạy cục bộ và nghiệm thu cuối

**Files:** Modify `README.md`, `.env.example`, `docker-compose.yml`, `test/README.md`; Create `docs/verification/final-acceptance.md`.

- [ ] README mô tả JDK 21, env, DB dev/test riêng, build/install trước test, mock server độc lập, ports và URL web/OpenAPI; không yêu cầu sửa source để chạy test.
- [ ] Chạy lại từ hướng dẫn bằng Java, Vite và MySQL cục bộ, không khởi động Docker hoặc xóa dữ liệu hiện có. Kiểm tra health/startup và ứng dụng sau restart vẫn còn đơn/trạng thái đối soát.
- [ ] Chạy build/install Backend, unit suite, integration suite và Frontend typecheck/build. Kiểm tra lệnh thoát 0 và nội dung báo cáo; không coi suite không có test là pass.
- [ ] Điền checklist 9 scenario, phân biệt môi trường mock/service thật/Sandbox và mọi dependency chưa có. Không ghi dự án hoàn tất nếu yêu cầu bắt buộc còn chưa nghiệm thu.
- [ ] Review diff cuối, bảo đảm chỉ 7 bảng nghiệp vụ (Flyway metadata không phải bảng nghiệp vụ); không test framework Frontend, không class/resource test trong artifact deploy.
- [ ] Commit `docs: finalize customer setup and acceptance evidence` nếu có quyền; báo danh sách file chưa commit nếu không có. Không tự push/deploy/merge.

## 5. Mẫu TDD cụ thể để thực thi task

Mẫu sau cho Task 15 minh họa ca quan trọng VNPAY chưa PAID không được gửi. Các type/constructor dưới đây là chữ ký nghiệp vụ của ShipmentSyncService/ShipmentSyncStateService được Task 15 tạo, không phải test hook. Interface ShippingClient được Task 10 định nghĩa. `tryClaim(long shipmentId, Instant now)` trả boolean sau kiểm tra nguyên tử điều kiện; `sync(long shipmentId)` trả void. Test không giả lập thành công của toàn bộ service cần kiểm thử.

```java
package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.shipment.service.ShipmentSyncService;
import com.dcpost.customer.shipment.service.ShipmentSyncStateService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ShipmentSyncServiceTest {
    /** Không gửi vận đơn khi chưa giành được quyền gửi hợp lệ. */
    @Test
    void Test_sync_001() {
        var state = mock(ShipmentSyncStateService.class);
        var shipping = mock(ShippingClient.class);
        var now = Instant.parse("2026-11-24T00:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        when(state.tryClaim(41L, now)).thenReturn(false);
        var service = new ShipmentSyncService(state, shipping, clock);

        service.sync(41L);

        verifyNoInteractions(shipping);
    }
}
```

Ca này phải đi cùng integration test ShipmentSyncClaimIT xác minh chính `tryClaim` từ chối VNPAY chưa PAID và không chỉ dựa vào mock. Khi tạo source, phần guard tối thiểu là:

```java
if (!state.tryClaim(shipmentId, clock.instant())) {
    return;
}
```

Thực hiện ca claim thành công và gửi đơn bằng RED/GREEN trước, sau đó mới thêm ca từ chối claim ở ví dụ trên để bắt hành vi gửi sai nếu có. Không cố ý thêm lỗi hoặc throw exception không liên quan để tạo RED giả. Nếu test mới đã pass vì implementation hiện tại đúng thì ghi là kiểm tra bổ sung đã pass, không bịa bằng chứng RED. Với API chưa có implementation, test mới chỉ compile được chưa phải bằng chứng RED đúng; đọc thông báo failure trước GREEN.

Mẫu cho Task 24 dùng assertion serialized response để xác nhận UI không mất kết quả tài chính: sau khi fixture tạo PAID + cancellation CONFIRMED và provider timeout, GET shipment phải có paymentStatus PAID, refundStatus PROCESSING; shipment không bị đổi sang vận chuyển FAILED. Đặt fixture/setup và assertion thực tế trong RefundConcurrencyIT trước implementation, kiểm tra riêng số bản ghi refunds bằng database và số lần provider được gọi. Không test private setter để thay cho hành vi này.

### Lệnh chọn test cho từng task Backend

Luôn chạy `mvn -f customer-service/pom.xml install` sau lần sửa Backend gần nhất. Bảng dưới cung cấp lệnh chọn test cụ thể; RED phải là hành vi mục tiêu chưa đáp ứng, GREEN phải có test thực sự được chạy, không có failures/errors. Khi dùng `-Dit.test`, vẫn chạy toàn bộ unit suite trước hoặc trong lifecycle verify. Không bỏ unit test để che lỗi.

| Task | Lệnh kiểm tra |
|---|---|
| 05 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=IdentitySchemaIT'` |
| 06 | `mvn -f test/backend/pom.xml '-Dtest=RegistrationServiceTest' test` |
| 07 | `mvn -f test/backend/pom.xml '-Dtest=AuthServiceTest,JwtTokenServiceTest' test` |
| 08 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=SecurityBoundaryIT'` và `mvn -f test/backend/pom.xml '-Dtest=RequestRateLimiterTest' test` |
| 09 | `mvn -f test/backend/pom.xml '-Dtest=ProfileServiceTest' test` |
| 10 | `mvn -f test/backend/pom.xml test '-Dtest=DevelopmentAdapterTest'` (không cần Docker/MySQL) |
| 11 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ShipmentSchemaIT'` và `mvn -f test/backend/pom.xml '-Dtest=TrackingCodeGeneratorTest' test` |
| 12 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ShipmentIdempotencyIT'` và `mvn -f test/backend/pom.xml '-Dtest=ShipmentCreationServiceTest' test` |
| 13 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=CounterShipmentIT'` |
| 14 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ShipmentOwnershipIT'` |
| 15 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ShipmentSyncClaimIT'` và `mvn -f test/backend/pom.xml '-Dtest=ShipmentSyncServiceTest' test` |
| 16 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ShipmentRecoveryIT'` |
| 17 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ErrorResponseIT'` |
| 19 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=FinancialSchemaIT'` |
| 20 | `mvn -f test/backend/pom.xml '-Dtest=PaymentServiceTest,VnPaySignerTest' test` |
| 21 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=PaymentNotificationIT'` và `mvn -f test/backend/pom.xml '-Dtest=PaymentReconciliationTest' test` |
| 22 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=CashPaymentIT,PaymentOwnershipIT'` |
| 23 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ShipmentCancellationIT'` |
| 24 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=RefundConcurrencyIT,RefundOwnershipIT'` và `mvn -f test/backend/pom.xml '-Dtest=RefundServiceTest' test` |
| 25 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=PublicTrackingIT'` |
| 26 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=DashboardOwnershipIT'` và `mvn -f test/backend/pom.xml '-Dtest=DashboardServiceTest' test` |
| 31 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=CustomerAcceptanceIT,FinancialRaceIT'` |
| 32 | `mvn -f test/backend/pom.xml verify -Pintegration '-Dit.test=ExternalAdapterContractIT'` |

Với Task 05 và 11 trước khi app context có schema, test migration được phép dùng JDBC/Flyway từ test module để kiểm tra metadata/constraint trực tiếp trên MySQL test. Không coi lỗi khởi động context do thiếu env là một assertion RED có ý nghĩa.

## 6. Ma trận phủ yêu cầu

| Yêu cầu PROJECT_SPEC | Task | Bằng chứng nghiệm thu |
|---|---|---|
| 1–4: scope, stack, ownership, roles | 01–04, 08, 10, 32 | Toolchain, dependency tree, security boundary, adapter contract |
| 5.1–5.2: auth/profile | 05–09, 27–28 | Unit/IT và checklist UI |
| 5.3, 6–8: shipment/counter/fee/cancel | 11–16, 23, 29 | Validation, idempotency, ownership, race và form flow |
| 9: payment/CASH/VNPay | 18–22, 30, 33 | Signing vector, callback/reconciliation, Sandbox |
| 10: refund | 19, 24, 30, 33 | Unique refund, concurrency, timeout và đối soát thật |
| 11: public tracking/privacy | 25, 28 | Public payload allowlist, rate limit và timeline |
| 12: 7 bảng, constraint/index/UTC | 05, 11, 19, 31, 34 | Migration IT và information_schema |
| 13–16: frontend/routes/backend organization | 02, 04, 09–17, 27–30 | Build, OpenAPI, checklist desktop/tablet/mobile |
| 17–18: API/reliability/security | 08, 10, 12–17, 20–25, 32–33 | Contract, timeout/recovery, review ownership/CSRF |
| 19: test isolation/TDD | 03 và từng task Backend, 31 | RED/GREEN evidence, test module và artifact scan |
| 20–21: structure/phases | 01–34 | File map và dependency order |
| 22: 9 acceptance scenario | 31–34 | CustomerAcceptanceIT + manual/Sandbox/service thật |
| 23: Superpowers, review, verification | Mọi task | Skill workflow, review từng checkpoint và báo cáo có bằng chứng |

## 7. Checkpoint review và bàn giao

- Sau Task 04: xác nhận setup, đúng JDK, cách ly test và packaging.
- Sau Task 09: review authentication/CSRF/thu hồi access token 24 giờ.
- Sau Task 17: review shipment/idempotency/scheduler/UNKNOWN, giữ 7 bảng.
- Sau Task 24: review tài chính, khóa đồng thời và PAID sau hủy.
- Sau Task 30: review toàn bộ trải nghiệm Customer bằng checklist thủ công.
- Sau Task 34: đối chiếu yêu cầu với bằng chứng thực tế, công khai dependency chưa hoàn tất.

Mỗi checkpoint dùng requesting-code-review và verification-before-completion; phản hồi kỹ thuật được xác minh theo receiving-code-review. Nếu dùng agent review thì chỉ dispatch theo workflow đã được chọn/ủy quyền; agent không được sửa cùng file đồng thời với người triển khai. Nếu gặp bug dùng systematic-debugging và test tái hiện trước sửa.

Plan được bàn giao để user duyệt. Có thể thực hiện trực tiếp bằng executing-plans theo từng nhóm task có checkpoint, hoặc dùng subagent-driven-development nếu user chọn. Việc duyệt thiết kế không tự động cho phép triển khai khi plan chưa được thống nhất.
