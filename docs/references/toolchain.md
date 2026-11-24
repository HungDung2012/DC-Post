# Toolchain Customer

Kiểm tra ngày 24/11/2026 trên Windows, PowerShell.

| Thành phần | Phiên bản | Kết quả |
|---|---|---|
| JDK cho dự án | Oracle Java 21.0.12.1 LTS | Đã chạy java/mvn bằng đường dẫn JDK 21 trong terminal |
| Java mặc định máy | Oracle Java 23 | Không thay đổi cấu hình toàn máy |
| Maven | 3.9.12 | Đã kiểm tra |
| Spring Boot | 3.5.16 | Bản vá nhánh 3.5 xác minh từ Maven Central |
| Springdoc | 2.8.17 | Nhánh 2.8 tương thích Boot 3.5 |
| Flyway | 11.20.3 | Bản BOM 11.7.2 cảnh báo MySQL 8.4; đã đối chiếu source adapter 11.20.3 hỗ trợ đến MySQL 9.4 |
| Node | 24.14.1 | Đáp ứng Vite yêu cầu >=22.12 |
| npm | 11.11.0 | Dùng npm.cmd trong PowerShell |
| Vite | 8.3.8 | Xác minh phiên bản và engines từ npm |
| React | 19.3.1 | Xác minh từ npm |
| Docker | 29.2.1 | CLI và daemon đã hoạt động |
| Docker Compose | 5.0.2 | CLI có sẵn |
| MySQL | 8.4.8 LTS | Đã truy vấn phiên bản/database trên MySQL test; image được pin digest |

Nguồn:

- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml
- https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml
- https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot
- https://registry.npmjs.org/vite/latest
- https://registry.npmjs.org/react/latest
- https://docs.spring.io/spring-boot/3.5/system-requirements.html

Các dependency Maven do Boot BOM quản lý; Springdoc và Flyway được pin trong POM.
Dependency npm được pin trực tiếp và bằng package-lock.json trong customer-web.
Maven dùng repository cache riêng `.cache/maven` qua `.mvn/maven.config` khi chạy từ root.
Docker base image được pin digest đã resolve từ registry, không dùng tag `latest`.
TypeScript 7 không còn tùy chọn `baseUrl`; alias dùng `paths` tương đối và Vite alias.
Host port 8080 bị Windows từ chối bind trong phiên triển khai này. Dùng host port
18080 (đã kiểm tra bind được), giữ nguyên port 8080 trong container.

Chọn JDK riêng cho terminal, không thay biến môi trường hệ thống:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12.1'
$env:Path = "$env:JAVA_HOME/bin;" + $env:Path
mvn -version
```
