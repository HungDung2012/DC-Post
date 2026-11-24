# PROJECT SPECIFICATION
# CUSTOMER SERVICE — SHIPMENT MANAGEMENT SYSTEM

> Cập nhật môi trường ngày 24/11/2026 theo yêu cầu chủ dự án: phát triển và kiểm thử cục bộ chạy Java/Maven, Vite và MySQL cài trực tiếp, không sử dụng Docker do giới hạn bộ nhớ. Các mục Docker/Compose bên dưới được giữ làm tham chiếu đóng gói, không phải điều kiện để code hoặc test. Các quyết định nghiệp vụ và MySQL vẫn giữ nguyên.

## 1. Tổng quan dự án

### 1.1. Giới thiệu

Tên dự án: Hệ thống quản lý vận đơn và theo dõi giao hàng.

Tên phân hệ: Customer Service.

Dự án được xây dựng theo kiến trúc Microservices, mô phỏng hoạt động của một doanh nghiệp cung cấp dịch vụ vận chuyển như GHN, GHTK hoặc Viettel Post.

Đây là website cung cấp dịch vụ gửi hàng và vận chuyển hàng hóa, không phải website thương mại điện tử.

Khách hàng có sẵn hàng hóa và sử dụng hệ thống để tạo vận đơn, thanh toán phí vận chuyển, quản lý vận đơn và theo dõi quá trình giao hàng.

### 1.2. Phạm vi trách nhiệm

Phạm vi dự án này chỉ bao gồm phân hệ Customer:

- Customer Frontend.
- Customer Backend.
- Customer Database.
- Tích hợp API với các Microservice khác.
- Tích hợp thanh toán và hoàn tiền qua VNPay Sandbox.

Các Microservice khác do những thành viên khác trong nhóm phụ trách.

Backend của phân hệ vận chuyển sử dụng ngôn ngữ Go.

Không tự ý thiết kế hoặc triển khai nghiệp vụ nội bộ của những Microservice khác.

### 1.3. Ngoài phạm vi

Không triển khai các chức năng sau:

- Admin Dashboard.
- Quản lý nhân viên kho.
- Quản lý kho hàng.
- Quản lý tồn kho.
- Nhập kho và xuất kho.
- Quản lý tài khoản shipper.
- Giao diện dành cho shipper.
- Phân công shipper lấy hàng hoặc giao hàng.
- Điều phối vận chuyển.
- Thu hộ COD.
- Tính toán phí vận chuyển.
- Quản lý bảng giá vận chuyển.
- Quản lý trạng thái vận chuyển.
- Quản lý lịch sử hành trình vận chuyển.
- API Gateway.
- Tối ưu tuyến đường.
- Website thương mại điện tử.
- Quản lý danh mục sản phẩm.

Những dữ liệu liên quan đến các nghiệp vụ bên ngoài sẽ được lấy thông qua API.

Không xây dựng lại chức năng của các Microservice khác chỉ để Customer Service có thể hoạt động độc lập.

## 2. Technology Stack

### 2.1. Frontend

- React.
- TypeScript.
- Vite.
- Tailwind CSS.
- shadcn/ui.
- React Router.
- TanStack Query.
- React Hook Form.
- Zod.
- Axios.
- Recharts.
- Lucide React.

### 2.2. Backend

- Java 21 LTS.
- Spring Boot.
- Maven.
- Spring Web.
- Spring Security.
- JWT Authentication.
- Spring Data JPA.
- Hibernate.
- Jakarta Validation.
- Spring RestClient.
- Springdoc OpenAPI.
- Flyway.
- JUnit 5 (chỉ trong module test độc lập).
- Mockito (chỉ trong module test độc lập).

### 2.3. Database và Infrastructure

- MySQL.
- Docker.
- Docker Compose.
- Git.
- GitHub.
- VNPay Sandbox.

Sử dụng các phiên bản thư viện tương thích với nhau.

Không bổ sung Redis, Kafka, RabbitMQ, Kubernetes hoặc các thành phần hạ tầng phức tạp nếu chưa có nhu cầu thực tế và chưa được chủ dự án đồng ý.

## 3. Kiến trúc hệ thống

### 3.1. Kiến trúc tổng thể

Customer Frontend giao tiếp trực tiếp với Customer Backend thông qua REST API.

Customer Backend chịu trách nhiệm:

- Xác thực và phân quyền.
- Xử lý nghiệp vụ khách hàng.
- Quản lý dữ liệu thuộc quyền sở hữu.
- Tích hợp API của các Microservice khác.
- Tổng hợp dữ liệu trả về cho Frontend.
- Tích hợp VNPay Sandbox.

Customer Backend sử dụng MySQL làm database riêng.

Giao tiếp giữa Customer Backend và các Microservice khác sử dụng REST API + JSON.

Không triển khai API Gateway.

Không cho phép Customer Frontend gọi trực tiếp các API nội bộ của service vận chuyển, kho hoặc tính phí.

Không truy cập trực tiếp database của các Microservice khác.

### 3.2. Quyền sở hữu dữ liệu

Customer Service sở hữu:

- Tài khoản khách hàng.
- Thông tin cá nhân.
- Dữ liệu gốc của vận đơn.
- Mã vận đơn.
- Thông tin người gửi.
- Thông tin người nhận.
- Thông tin hàng hóa khai báo.
- Khối lượng và kích thước kiện hàng.
- Hình thức gửi hàng.
- Hình thức tạo vận đơn.
- Phí vận chuyển đã xác nhận.
- Lịch sử thanh toán.
- Lịch sử hoàn tiền.
- Thông tin yêu cầu và kết quả hủy đơn.

Customer Service không sở hữu:

- Trạng thái vận chuyển chính thức.
- Lịch sử hành trình vận chuyển.
- Thông tin kho hiện tại.
- Thông tin phân công shipper.
- Vị trí shipper.
- Kết quả giao hàng.
- Quy tắc tính phí vận chuyển.
- Danh sách và thông tin nội bộ của kho.

Những dữ liệu trên được lấy qua API của service sở hữu tương ứng.

### 3.3. Kiến trúc tích hợp

Áp dụng Interface và Adapter Pattern.

Tách biệt nghiệp vụ Customer khỏi cách giao tiếp HTTP với các service bên ngoài.

Các integration client dự kiến:

- ShippingClient.
- PricingClient.
- WarehouseClient.
- VnPayClient.

Tên gọi chỉ là đề xuất tổ chức source code.

Mỗi integration client có thể có:

- Interface.
- HTTP implementation.
- Request DTO.
- Response DTO.
- Exception handling.
- Response mapping.

Không để business logic phụ thuộc trực tiếp vào cấu trúc JSON của service bên ngoài.

Các API Contract với thành viên khác chưa được xác định chính thức.

Không tự coi API Contract giả lập là API Contract cuối cùng.

Khi chưa có API thật, sử dụng mock/stub cho dependency bên ngoài trong thư mục test/ độc lập để kiểm thử Customer Service. Nếu cần chạy ứng dụng trong lúc phát triển, có thể sử dụng HTTP mock server độc lập đặt trong test/, cấu hình URL thông qua cơ chế cấu hình endpoint thông thường của ứng dụng.

Không đặt Mock Adapter, fake client, mock bean, dữ liệu giả hoặc profile dành riêng cho kiểm thử trong source code chính. Customer Backend chỉ chứa HTTP Adapter thực tế và các abstraction phục vụ kiến trúc nghiệp vụ.

Môi trường tích hợp chính thức phải sử dụng HTTP Adapter thực tế kết nối tới API của service tương ứng. Không tự động fallback sang dữ liệu giả khi API thật không khả dụng.

## 4. Đối tượng sử dụng

### 4.1. CUSTOMER

Gộp khách hàng cá nhân và Shop thành một ROLE duy nhất: CUSTOMER.

Không tạo ROLE SHOP riêng.

Khách hàng phải đăng nhập để:

- Tạo vận đơn.
- Quản lý vận đơn.
- Thanh toán.
- Hủy vận đơn.
- Xem lịch sử thanh toán.
- Xem lịch sử hoàn tiền.
- Quản lý tài khoản.
- Xem thống kê vận đơn cá nhân.

Khách hàng chỉ được truy cập các vận đơn thuộc quyền sở hữu của mình.

### 4.2. GUEST

Guest là người dùng chưa đăng nhập.

Không lưu Guest thành ROLE trong database.

Guest chỉ có nghiệp vụ tra cứu vận đơn công khai.

Guest không được:

- Tạo vận đơn online.
- Quản lý vận đơn cá nhân.
- Hủy vận đơn.
- Thanh toán.
- Truy cập thông tin tài khoản của người khác.

Guest có thể xem các nội dung giới thiệu dịch vụ tĩnh trên website.

### 4.3. Nhân viên bưu cục

Nhân viên bưu cục thuộc phân hệ của thành viên khác.

Không triển khai giao diện nhân viên trong dự án Customer.

Customer Service cung cấp API nội bộ phục vụ:

- Tạo vận đơn hộ khách hàng tại bưu cục.
- Xác nhận kết quả thu phí vận chuyển bằng tiền mặt.

Các API nội bộ phải có cơ chế xác thực và phân quyền phù hợp.

Không cho phép CUSTOMER thông thường tự gọi API nội bộ để giả mạo nhân viên.

## 5. Danh sách chức năng

### 5.1. Authentication

- Đăng ký tài khoản Customer.
- Đăng nhập.
- Đăng xuất.
- Xác thực bằng JWT.
- Quản lý phiên xác thực.
- Kiểm tra tài khoản đang hoạt động.

Người dùng đăng ký thông qua website chỉ được cấp ROLE CUSTOMER.

### 5.2. Account Management

- Xem thông tin cá nhân.
- Cập nhật thông tin cá nhân.
- Đổi mật khẩu.
- Quản lý địa chỉ mặc định.

### 5.3. Shipment Management

- Tạo vận đơn online.
- Chọn hình thức tiếp nhận hàng.
- Khai báo thông tin người gửi.
- Khai báo thông tin người nhận.
- Khai báo danh sách hàng hóa.
- Khai báo kích thước và khối lượng kiện hàng.
- Nhận phí vận chuyển dự kiến từ API.
- Chọn phương thức thanh toán.
- Xác nhận tạo vận đơn.
- Xem danh sách vận đơn cá nhân.
- Tìm kiếm vận đơn.
- Lọc vận đơn.
- Phân trang.
- Xem chi tiết vận đơn.
- Yêu cầu hủy vận đơn.
- Xem lịch sử giao hàng.

Không hỗ trợ chỉnh sửa vận đơn sau khi tạo thành công.

Không hỗ trợ xóa vật lý vận đơn.

### 5.4. Shipment Tracking

- Tra cứu vận đơn công khai.
- Xem trạng thái vận chuyển hiện tại.
- Xem lịch sử hành trình.
- Xem timeline vận chuyển.
- Xem kết quả giao hàng.
- Xem vị trí gần đúng của vận đơn nếu API bên ngoài hỗ trợ.

Không tự xây dựng hệ thống cập nhật trạng thái hoặc GPS.

### 5.5. Payment

- Thanh toán online bằng VNPay Sandbox.
- Thanh toán tiền mặt.
- Xem kết quả thanh toán.
- Thanh toán lại nếu giao dịch trước thất bại và vận đơn còn đủ điều kiện.
- Xem lịch sử giao dịch.

Không triển khai COD.

### 5.6. Refund

- Tự động hoàn tiền toàn bộ qua VNPay khi vận đơn đủ điều kiện.
- Xem trạng thái hoàn tiền.
- Xem lịch sử hoàn tiền.
- Xử lý trường hợp hoàn tiền thất bại hoặc chưa xác định kết quả.

Không hỗ trợ hoàn tiền từng phần trong MVP.

### 5.7. Customer Dashboard

- Tổng số vận đơn cá nhân.
- Số vận đơn chờ xử lý.
- Số vận đơn đang vận chuyển.
- Số vận đơn giao thành công.
- Số vận đơn giao thất bại.
- Số vận đơn đã hủy.
- Biểu đồ số lượng vận đơn theo thời gian.
- Danh sách vận đơn gần đây.
- Tổng phí vận chuyển đã thanh toán.

Dữ liệu thống kê phải giới hạn trong phạm vi tài khoản đang đăng nhập.

Các nhóm trạng thái hiển thị phải được ánh xạ theo dữ liệu API vận chuyển thực tế, không tự định nghĩa quy trình vận chuyển thay service khác.

## 6. Nghiệp vụ tạo vận đơn

### 6.1. Quy tắc kiện hàng

Một vận đơn tương ứng với một kiện hàng.

Một kiện hàng có thể chứa nhiều mặt hàng khác nhau.

Một vận đơn có:

- Một mã vận đơn.
- Một khối lượng tổng.
- Một bộ kích thước.
- Một địa chỉ gửi hàng.
- Một địa chỉ nhận hàng.
- Một mức phí vận chuyển.
- Một danh sách hàng hóa khai báo.

Nếu khách hàng muốn gửi nhiều kiện độc lập thì tạo nhiều vận đơn.

Không bổ sung bảng shipment_packages.

Không triển khai nghiệp vụ chia một vận đơn thành nhiều kiện.

Không sử dụng Order làm thực thể trung gian.

Shipment là thực thể nghiệp vụ trung tâm.

### 6.2. Hình thức tạo vận đơn

Hỗ trợ hai hình thức:

ONLINE: Customer đăng nhập website và tự tạo vận đơn.

COUNTER: Nhân viên bưu cục tạo vận đơn hộ khách hàng thông qua API.

### 6.3. Hình thức tiếp nhận hàng

Hỗ trợ:

HOME_PICKUP: Shipper đến tận địa chỉ người gửi để lấy hàng.

DROP_OFF: Khách hàng tự mang hàng đến bưu cục.

Các tổ hợp hợp lệ:

| Creation channel | Pickup method |
|---|---|
| ONLINE | HOME_PICKUP |
| ONLINE | DROP_OFF |
| COUNTER | DROP_OFF |

Không cho phép COUNTER kết hợp HOME_PICKUP trong MVP.

### 6.4. Tạo vận đơn online

Luồng xử lý:

1. Customer đăng nhập.
2. Customer mở chức năng tạo vận đơn.
3. Hệ thống điền mặc định thông tin người gửi từ hồ sơ khách hàng.
4. Customer nhập hoặc xác nhận thông tin gửi hàng.
5. Customer chọn hình thức tiếp nhận hàng.
6. Nếu chọn DROP_OFF, Customer chọn bưu cục từ dữ liệu API.
7. Customer nhập thông tin người nhận.
8. Customer khai báo danh sách hàng hóa.
9. Customer nhập khối lượng và kích thước kiện hàng.
10. Customer Backend gọi API tính phí vận chuyển.
11. Hệ thống hiển thị phí vận chuyển.
12. Customer chọn phương thức thanh toán.
13. Customer kiểm tra và xác nhận thông tin.
14. Backend kiểm tra lại dữ liệu và phí vận chuyển.
15. Backend sinh mã vận đơn duy nhất.
16. Backend lưu thông tin gốc của vận đơn và hàng hóa vào database.
17. Nếu chọn CASH, Backend lưu tác vụ gửi vận đơn trong cùng transaction tạo vận đơn; scheduler xử lý sau khi transaction đã commit.
18. Nếu chọn VNPAY, Customer thực hiện thanh toán. Backend chỉ lưu tác vụ gửi vận đơn khi đã xác nhận thanh toán thành công qua IPN hoặc kết quả truy vấn giao dịch được xác minh; việc ghi nhận thanh toán thành công và tạo tác vụ phải nằm trong cùng transaction.
19. Scheduler gửi thông tin vận đơn sang service vận chuyển ngoài database transaction và lưu kết quả đồng bộ bằng transaction ngắn. Callback hoặc tác vụ lặp không được tạo yêu cầu nghiệp vụ trùng.
20. Website hiển thị riêng kết quả tạo vận đơn, thanh toán và đồng bộ. Chỉ hiển thị dữ liệu theo dõi vận chuyển khi API cung cấp; không tự tạo trạng thái vận chuyển.

Không gửi đơn VNPAY sang service vận chuyển khi giao dịch còn chờ xử lý, thất bại hoặc chưa xác định kết quả. Return URL không kích hoạt tác vụ gửi vận đơn.

Nếu đã thanh toán thành công nhưng đồng bộ chưa thành công, giữ nguyên dữ liệu vận đơn và giao dịch thực tế, đồng thời theo dõi tác vụ để đối soát hoặc retry an toàn. Không tự động hoàn tiền chỉ vì lỗi đồng bộ.

Trong trường hợp này, giao diện hiển thị tiến trình là "Đang xử lý", kèm mô tả "Đã thanh toán, đang đồng bộ vận đơn". Trạng thái thanh toán trong database vẫn là PAID theo kết quả đã xác minh; trạng thái đồng bộ được lưu riêng trên shipments để scheduler theo dõi, không cần bảng integration_tasks riêng. Không chuyển giao dịch đã thanh toán về PENDING hoặc cho phép thanh toán lại vì lỗi đồng bộ. Nhãn tiến trình này không phải trạng thái vận chuyển chính thức.

Đây là thứ tự xử lý đã thống nhất cho phía Customer. Các yêu cầu về tiếp nhận đơn, idempotency và đối soát vẫn phải thống nhất với nhóm phụ trách API; không mặc định service bên ngoài đã hỗ trợ các khả năng này.

### 6.5. Tạo vận đơn trực tiếp tại bưu cục

Khách hàng gửi hàng tại bưu cục không bắt buộc có tài khoản.

Nhân viên sử dụng giao diện thuộc phân hệ khác và gọi API của Customer Service để tạo vận đơn.

Quy tắc dữ liệu:

- customer_id có thể NULL.
- created_by là ID nhân viên thực hiện.
- creation_channel = COUNTER.
- pickup_method = DROP_OFF.
- Lưu trực tiếp thông tin người gửi.
- Lưu thông tin người nhận và hàng hóa.
- Sinh mã vận đơn duy nhất.

Nếu người gửi đã có tài khoản và được liên kết hợp lệ khi tạo đơn, có thể lưu customer_id tương ứng.

Nếu không có tài khoản, khách hàng sử dụng mã vận đơn để tra cứu công khai.

Không triển khai chức năng tự động liên kết vận đơn cũ sau khi khách đăng ký tài khoản.

### 6.6. Thông tin người gửi

Đối với Customer đã đăng nhập, sử dụng hồ sơ tài khoản để điền thông tin mặc định.

Tuy nhiên, lưu snapshot thông tin gửi hàng trong vận đơn để việc cập nhật hồ sơ cá nhân không làm thay đổi lịch sử vận đơn.

Dữ liệu tối thiểu:

- Họ và tên.
- Số điện thoại.
- Địa chỉ gửi hàng.

### 6.7. Thông tin người nhận

- Họ và tên.
- Số điện thoại.
- Địa chỉ nhận hàng.

### 6.8. Thông tin kiện hàng

- Khối lượng, đơn vị kg.
- Chiều dài, đơn vị cm.
- Chiều rộng, đơn vị cm.
- Chiều cao, đơn vị cm.
- Danh sách mặt hàng.
- Ghi chú vận chuyển.

Mỗi mặt hàng gồm:

- Tên hàng hóa.
- Loại hàng hóa.
- Số lượng.
- Tổng giá trị khai báo của dòng hàng.
- Ghi chú nếu có.

Giá trị khai báo hàng hóa không phải COD.

Không cần bảng products.

## 7. Tính phí vận chuyển

Customer Service không sở hữu thuật toán tính phí.

Customer Backend gửi các thông tin cần thiết sang API tính phí và nhận kết quả.

Không tự xây dựng:

- Công thức tính phí.
- Bảng giá.
- Thuật toán khoảng cách.
- Quy tắc phụ phí.
- Cấu hình chính sách vận chuyển.

Khi khách hàng thay đổi dữ liệu ảnh hưởng đến phí, Frontend có thể yêu cầu tính lại phí.

Backend phải xác minh lại mức phí khi tạo vận đơn.

Không tin tưởng giá trị phí do Frontend tự gửi.

Lưu mức phí đã xác nhận vào shipments.shipping_fee.

Nếu API tính phí không khả dụng, không tự sinh mức phí giả trong môi trường tích hợp chính thức.

Nếu mức phí thay đổi trước khi xác nhận, cần thông báo và yêu cầu khách hàng xác nhận mức phí mới.

## 8. Nghiệp vụ quản lý và hủy vận đơn

### 8.1. Danh sách vận đơn

Customer chỉ được xem vận đơn có customer_id tương ứng với tài khoản của mình.

Hỗ trợ:

- Tìm kiếm theo mã vận đơn.
- Tìm kiếm theo thông tin người nhận trong phạm vi đơn của mình.
- Lọc theo thời gian.
- Lọc theo trạng thái nếu API vận chuyển hỗ trợ.
- Lọc theo phương thức hoặc trạng thái thanh toán.
- Phân trang.
- Sắp xếp theo thời gian tạo.

Đối với lọc theo trạng thái vận chuyển, cần thiết kế tích hợp phù hợp để bảo đảm kết quả phân trang chính xác.

Không mặc định lấy trạng thái từng đơn bằng số lượng lớn HTTP request nếu có thể sử dụng batch API.

### 8.2. Chi tiết vận đơn

Customer Backend tổng hợp:

- Dữ liệu gốc của vận đơn từ Customer Database.
- Danh sách hàng hóa.
- Thông tin thanh toán.
- Thông tin hoàn tiền.
- Trạng thái vận chuyển từ API.
- Lịch sử hành trình từ API.
- Kết quả giao hàng từ API.

Không cho phép xem thông tin riêng tư của vận đơn thuộc khách hàng khác.

### 8.3. Không chỉnh sửa vận đơn

Sau khi vận đơn được tạo thành công, không hỗ trợ cập nhật thông tin gửi hàng, nhận hàng hoặc hàng hóa.

Nếu khách nhập sai thông tin, khách có thể yêu cầu hủy vận đơn và tạo vận đơn mới nếu còn đủ điều kiện.

### 8.4. Hủy vận đơn

Khách hàng chỉ được yêu cầu hủy vận đơn thuộc quyền sở hữu.

Luồng xử lý đối với vận đơn đã được service vận chuyển tiếp nhận:

1. Customer chọn vận đơn cần hủy.
2. Customer nhập lý do hủy.
3. Customer xác nhận.
4. Backend kiểm tra quyền sở hữu.
5. Backend gửi yêu cầu hủy tới API của service vận chuyển.
6. Service vận chuyển kiểm tra điều kiện và cập nhật trạng thái vận chuyển.
7. Customer Backend tiếp nhận kết quả.
8. Nếu hủy thành công, ghi nhận thông tin hủy trong Customer Database.
9. Nếu đã thanh toán VNPay thành công, tạo yêu cầu hoàn tiền.
10. Trả kết quả cho khách hàng.

Không tự quyết định điều kiện chuyển đổi trạng thái vận chuyển ngoài API Contract đã thống nhất.

Đối với vận đơn đã được tiếp nhận, service vận chuyển chịu trách nhiệm xác nhận đơn còn được phép hủy hay không.

Ngoại lệ đối với vận đơn chắc chắn chưa từng được gửi sang service vận chuyển:

- Customer Backend được xác nhận hủy tại chỗ sau khi kiểm tra quyền sở hữu.
- Việc xác nhận hủy và vô hiệu hóa tác vụ gửi vận đơn phải được thực hiện nguyên tử trong cùng transaction, có kiểm soát đồng thời với scheduler và xử lý kết quả thanh toán.
- Scheduler phải giành quyền gửi bằng cập nhật nguyên tử trước khi bắt đầu HTTP; chỉ được hủy tại chỗ nếu chưa có worker giành quyền gửi và chưa từng có lần gửi có khả năng đã tới service bên ngoài.
- Không được suy luận rằng đơn chưa gửi chỉ vì tác vụ đang chờ retry hoặc không có phản hồi thành công. Nếu tác vụ đang gửi, worker dừng sau khi giành quyền gửi hoặc kết quả gửi chưa xác định, phải đối soát trước; không xác nhận hủy tại chỗ.
- Ghi nhận nguồn xác nhận hủy là Customer đối với ngoại lệ này; không tạo hoặc thay đổi trạng thái vận chuyển chính thức.
- Nếu đã có giao dịch VNPay thành công, tạo yêu cầu hoàn tiền toàn phần theo mục 10. Nếu kết quả thanh toán thành công đến sau khi hủy tại chỗ, ghi nhận giao dịch thực tế và tạo yêu cầu hoàn tiền; không kích hoạt lại tác vụ gửi đơn.

Nếu API hủy không phản hồi hoặc kết quả chưa xác định:

- Không thông báo hủy thành công.
- Không hoàn tiền ngay.
- Kiểm tra lại trạng thái thực tế.
- Sử dụng cơ chế chống xử lý trùng khi gửi lại yêu cầu.

Không xóa dữ liệu vận đơn đã hủy.

## 9. Thanh toán

### 9.1. Phương thức thanh toán

Hỗ trợ:

- VNPAY.
- CASH.

Không triển khai COD.

### 9.2. VNPay Sandbox

Tích hợp VNPay Payment API trong môi trường sandbox.

Luồng nghiệp vụ:

1. Customer chọn thanh toán VNPay.
2. Customer Backend tạo giao dịch thanh toán.
3. Sinh mã giao dịch duy nhất.
4. Backend tạo URL thanh toán có chữ ký hợp lệ.
5. Frontend chuyển hướng khách hàng đến VNPay Sandbox.
6. Customer thực hiện thanh toán.
7. VNPay gửi thông báo kết quả về backend.
8. Backend xác minh thông tin giao dịch.
9. Backend cập nhật trạng thái thanh toán.
10. Customer xem kết quả trên website.

Phân biệt Return URL và IPN URL.

Return URL phục vụ điều hướng và hiển thị kết quả.

Không sử dụng Return URL làm nguồn duy nhất để xác nhận thanh toán thành công.

IPN hoặc kết quả truy vấn giao dịch đã được xác minh là căn cứ cập nhật thanh toán.

Backend cần kiểm tra:

- Chữ ký.
- Mã giao dịch.
- Số tiền.
- Trạng thái giao dịch.
- Tính hợp lệ của callback.
- Giao dịch đã được xử lý hay chưa.

Không để lộ VNPay Hash Secret trên Frontend.

Tất cả secret được lưu thông qua biến môi trường.

Tham khảo tài liệu chính thức:

https://sandbox.vnpayment.vn/apis/docs/

### 9.3. Thanh toán tiền mặt

Khách hàng thanh toán phí vận chuyển khi shipper đến lấy hàng hoặc tại bưu cục.

Customer Service ghi nhận phương thức CASH.

Service thực hiện thu tiền gửi kết quả thông qua API nội bộ.

Customer Service xác minh quyền gọi API và cập nhật trạng thái thanh toán.

Không cho phép khách hàng tự xác nhận đã thanh toán tiền mặt.

Không triển khai giao diện thu tiền dành cho nhân viên hoặc shipper.

### 9.4. Quy tắc thanh toán

- Tiền tệ VND.
- Số tiền thanh toán lấy từ mức phí đã xác nhận ở backend.
- Không sử dụng float hoặc double để xử lý tiền.
- Một vận đơn có thể có nhiều lần thử thanh toán.
- Chống việc thu phí thành công nhiều lần.
- Nếu có giao dịch chưa xác định kết quả, cần truy vấn trước khi cho phép thanh toán lại.
- Không cho phép thanh toán mới đối với vận đơn đã hủy.
- Nếu callback thành công đến sau khi vận đơn đã hủy, phải ghi nhận giao dịch thực tế và xử lý hoàn tiền phù hợp.
- Thông báo rõ giao dịch đang sử dụng môi trường sandbox.

### 9.5. Trạng thái thanh toán đề xuất

- PENDING.
- PAID.
- FAILED.
- EXPIRED.

Có thể bổ sung trạng thái kỹ thuật nếu cần phục vụ xử lý giao dịch chưa xác định kết quả.

Trạng thái thanh toán độc lập với trạng thái vận chuyển và hoàn tiền.

## 10. Hoàn tiền

### 10.1. Điều kiện hoàn tiền

Tự động hoàn tiền toàn bộ nếu:

- Vận đơn đã được service vận chuyển xác nhận hủy thành công, hoặc được Customer xác nhận hủy tại chỗ theo đúng ngoại lệ chưa từng gửi tại mục 8.4.
- Giao dịch VNPay đã thanh toán thành công.
- Giao dịch chưa được hoàn tiền toàn phần.

Không hoàn tiền một phần.

Không hoàn tiền cho giao dịch chưa thanh toán.

Không sử dụng VNPay để hoàn tiền mặt.

Tiền mặt đã thu cần xử lý theo quy trình phối hợp với service thu tiền, không tự mở rộng nghiệp vụ ngoài phạm vi đã thống nhất.

### 10.2. Quy trình

1. Xác nhận vận đơn đã hủy.
2. Kiểm tra giao dịch thanh toán.
3. Tạo bản ghi yêu cầu hoàn tiền.
4. Gọi VNPay Refund API.
5. Xác minh response.
6. Cập nhật trạng thái hoàn tiền.
7. Hiển thị kết quả cho Customer.
8. Nếu kết quả chưa xác định, sử dụng cơ chế truy vấn và đối chiếu phù hợp trước khi gửi lại.

Không đánh dấu hoàn tiền thành công chỉ vì API đã tiếp nhận yêu cầu.

Không gửi lại yêu cầu hoàn tiền một cách mù quáng khi xảy ra timeout.

Nếu hoàn tiền thất bại, bản ghi hoàn tiền chuyển sang trạng thái FAILED; kết quả hủy vận đơn đã được xác nhận và giao dịch thanh toán thực tế được giữ nguyên.

### 10.3. Trạng thái hoàn tiền

- PENDING.
- PROCESSING.
- SUCCESS.
- FAILED.

Không gộp trạng thái hoàn tiền với trạng thái thanh toán hoặc vận chuyển.

## 11. Tra cứu và theo dõi vận đơn

### 11.1. Tra cứu công khai

Guest hoặc Customer có thể nhập mã vận đơn để tra cứu.

Backend sử dụng mã vận đơn để lấy dữ liệu hành trình từ service vận chuyển.

Thông tin công khai gồm:

- Mã vận đơn.
- Trạng thái hiện tại.
- Thời gian cập nhật gần nhất.
- Timeline hành trình được phép công khai.
- Khu vực xử lý nếu API hỗ trợ.
- Kết quả giao hàng.

Không công khai:

- Số điện thoại đầy đủ.
- Địa chỉ nhà chi tiết.
- Thông tin tài khoản.
- Thông tin thanh toán.
- Thông tin hoàn tiền.
- Dữ liệu nội bộ của nhân viên.
- Tọa độ GPS chính xác của shipper.

Áp dụng rate limiting hợp lý.

Mã vận đơn phải đủ khó đoán, tránh sử dụng ID tuần tự trực tiếp làm tracking code công khai.

### 11.2. Timeline

Hiển thị các sự kiện do service vận chuyển cung cấp.

Không tạo bảng shipment_statuses hoặc shipment_tracking_events trong Customer Database.

Frontend chỉ ánh xạ dữ liệu được cung cấp sang nhãn hiển thị tiếng Việt, màu sắc và biểu tượng phù hợp.

Nếu API không cung cấp thông tin, hiển thị thông báo tương ứng thay vì tự tạo sự kiện giả.

### 11.3. Cập nhật dữ liệu

Có thể sử dụng polling thông qua TanStack Query để cập nhật dữ liệu gần thời gian thực.

Không bắt buộc xây dựng WebSocket hoặc SSE.

Khi service vận chuyển không khả dụng, hiển thị trạng thái không thể cập nhật dữ liệu.

Không tự chuyển vận đơn sang trạng thái vận chuyển khác.

## 12. Database Design

Sử dụng MySQL.

Customer Database có 7 bảng nghiệp vụ chính.

Các kiểu dữ liệu dưới đây là thiết kế logic. Cần hoàn thiện constraint, index, nullable và migration khi triển khai.

### 12.1. users

- user_id: BIGINT, PK.
- full_name: VARCHAR(255), NOT NULL.
- email: VARCHAR(255), UNIQUE.
- phone: VARCHAR(20), UNIQUE.
- password_hash: VARCHAR(255), NOT NULL.
- address: VARCHAR(500), NULL.
- is_active: BOOLEAN.
- created_at: DATETIME.
- updated_at: DATETIME.

### 12.2. roles

- role_id: INT, PK.
- role_name: VARCHAR(50), UNIQUE.

### 12.3. user_roles

- user_id: BIGINT, PK, FK.
- role_id: INT, PK, FK.

Sử dụng composite primary key.

### 12.4. shipments

- shipment_id: BIGINT, PK.
- customer_id: BIGINT, FK, NULLABLE.
- tracking_code: VARCHAR(50), UNIQUE.
- sender_name: VARCHAR(255).
- sender_phone: VARCHAR(20).
- sender_address: VARCHAR(500).
- recipient_name: VARCHAR(255).
- recipient_phone: VARCHAR(20).
- recipient_address: VARCHAR(500).
- weight: DECIMAL(10,3).
- length: DECIMAL(10,2).
- width: DECIMAL(10,2).
- height: DECIMAL(10,2).
- shipping_fee: DECIMAL(15,2).
- pickup_method: VARCHAR(20).
- creation_channel: VARCHAR(20).
- drop_off_warehouse_id: BIGINT, NULLABLE.
- created_by: BIGINT.
- note: TEXT, NULLABLE.
- cancel_reason: VARCHAR(500), NULLABLE.
- cancelled_at: DATETIME, NULLABLE.
- cancelled_by: BIGINT, NULLABLE.
- created_at: DATETIME.

Không lưu status_id, shipper_id, current_warehouse_id hoặc cod_amount.

Thông tin hủy trong bảng này phục vụ nghiệp vụ Customer, không thay thế nguồn trạng thái vận chuyển chính thức.

Các ID người thực hiện thuộc service khác chỉ là tham chiếu logic và phải xác định được nguồn định danh.

### 12.5. shipment_items

- shipment_item_id: BIGINT, PK.
- shipment_id: BIGINT, FK.
- item_name: VARCHAR(255).
- item_category: VARCHAR(100).
- quantity: INT.
- declared_value: DECIMAL(15,2).
- note: VARCHAR(500), NULLABLE.

declared_value là tổng giá trị khai báo của dòng hàng.

### 12.6. payments

- payment_id: BIGINT, PK.
- shipment_id: BIGINT, FK.
- amount: DECIMAL(15,2).
- payment_method: VARCHAR(20).
- payment_status: VARCHAR(30).
- transaction_ref: VARCHAR(100), UNIQUE.
- provider_transaction_id: VARCHAR(100), NULLABLE.
- response_code: VARCHAR(10), NULLABLE.
- created_at: DATETIME.
- updated_at: DATETIME.
- paid_at: DATETIME, NULLABLE.

Cần lưu đủ thông tin giao dịch VNPay gốc theo tài liệu chính thức để phục vụ truy vấn và hoàn tiền.

### 12.7. refunds

- refund_id: BIGINT, PK.
- payment_id: BIGINT, FK, UNIQUE.
- refund_amount: DECIMAL(15,2).
- refund_status: VARCHAR(30).
- request_id: VARCHAR(32), UNIQUE.
- provider_transaction_id: VARCHAR(100), NULLABLE.
- response_code: VARCHAR(10), NULLABLE.
- failure_reason: VARCHAR(500), NULLABLE.
- created_at: DATETIME.
- updated_at: DATETIME.
- refunded_at: DATETIME, NULLABLE.

Mỗi giao dịch thanh toán có tối đa một bản ghi hoàn tiền nghiệp vụ toàn phần.

Cần phân biệt bản ghi hoàn tiền nghiệp vụ với các lần gửi yêu cầu kỹ thuật nếu phải retry.

### 12.8. Quan hệ

- users N:N roles thông qua user_roles.
- users 1:N shipments.
- Một shipment có thể không liên kết tài khoản nếu tạo tại bưu cục.
- shipments 1:N shipment_items.
- shipments 1:N payments.
- payments 1:0..1 refunds.

### 12.9. Quy tắc database

- Sử dụng Flyway Migration.
- Foreign Key chỉ áp dụng cho các bảng cùng database.
- Không tạo Foreign Key vật lý xuyên Microservice.
- Sử dụng unique constraint phù hợp.
- Tạo index cho tracking_code, customer_id và các trường thường xuyên tìm kiếm.
- Sử dụng BigDecimal trong Java đối với dữ liệu tiền.
- Khối lượng và số lượng hàng hóa phải hợp lệ.
- Không lưu mật khẩu dạng plaintext.
- Không xóa lịch sử giao dịch.
- Sử dụng transaction cho các thao tác thay đổi nhiều bảng trong Customer Database.
- Kiểm soát concurrent requests.
- Kiểm soát duplicate payment và duplicate refund.
- Lưu thời gian nhất quán, ưu tiên UTC và hiển thị theo Asia/Ho_Chi_Minh.

Thiết kế đã thống nhất giữ nguyên 7 bảng hiện có, bổ sung các trường kỹ thuật cần thiết trên users, shipments, payments và refunds để phục vụ phiên đăng nhập, idempotency, đồng bộ và đối soát. Scheduler xử lý trực tiếp các bản ghi nghiệp vụ; "tác vụ" trong tài liệu không có nghĩa là một bảng hàng đợi riêng.

Không bổ sung auth_sessions, idempotency_records, integration_tasks hoặc integration_attempts trong phạm vi hiện tại. Mọi đề xuất thêm bảng sau này phải có lý do cụ thể và được chủ dự án đồng ý trước.

Không tự mở rộng thêm bảng nghiệp vụ của các service khác.

Mọi thay đổi lớn đối với database phải được giải thích và thống nhất trước.

## 13. Frontend Design

### 13.1. Tổng quan

Một ứng dụng React duy nhất, gồm hai layout:

- PublicLayout.
- CustomerLayout.

Giao diện tiếng Việt.

Phong cách hiện đại, chuyên nghiệp, phù hợp lĩnh vực logistics.

Responsive trên desktop, tablet và mobile.

Sử dụng chung Design System.

### 13.2. Public Website

Giao diện công khai trước khi đăng nhập.

Trang chủ phải có hình thức của website doanh nghiệp vận chuyển thực tế, không phải dashboard quản trị.

Các thành phần:

- Header.
- Logo.
- Thanh điều hướng ngang.
- Hero Section.
- Banner giới thiệu dịch vụ vận chuyển.
- Form tra cứu vận đơn nổi bật.
- Giới thiệu dịch vụ lấy hàng tận nơi.
- Giới thiệu dịch vụ gửi tại bưu cục.
- Hướng dẫn quy trình gửi hàng.
- Call To Action đăng ký hoặc tạo vận đơn.
- Footer.

Các phần giới thiệu dịch vụ có thể sử dụng nội dung tĩnh.

Không làm phát sinh thêm nghiệp vụ backend không cần thiết.

Các trang công khai:

- Trang chủ.
- Trang tra cứu vận đơn.
- Trang kết quả tra cứu.
- Trang đăng nhập.
- Trang đăng ký.

Nếu Guest chọn gửi hàng ngay, chuyển đến trang đăng nhập.

### 13.3. Customer Dashboard

Giao diện sau khi đăng nhập.

Sử dụng phong cách dashboard quản trị chuyên nghiệp, đáp ứng cả khách hàng cá nhân và khách gửi hàng thường xuyên.

Layout gồm:

- Sidebar.
- Header.
- Main Content.
- Menu tài khoản.

Sidebar gồm:

QUẢN LÝ VẬN ĐƠN

- Tổng quan.
- Tạo vận đơn.
- Quản lý vận đơn.
- Tra cứu vận đơn.

TÀI CHÍNH

- Lịch sử thanh toán.
- Hoàn tiền.

TÀI KHOẢN

- Thông tin cá nhân.
- Đổi mật khẩu.
- Đăng xuất.

### 13.4. Trang Dashboard

Hiển thị:

- Các thẻ thống kê.
- Biểu đồ số lượng vận đơn.
- Danh sách vận đơn gần đây.
- Nút tạo vận đơn nhanh.
- Bộ lọc khoảng thời gian.

Không hiển thị dữ liệu của khách hàng khác.

Không sử dụng số liệu hardcode trong môi trường tích hợp chính thức.

### 13.5. Trang tạo vận đơn

Sử dụng form chia bước.

Bước 1: Thông tin người gửi.

- Điền mặc định từ tài khoản.
- Địa chỉ gửi hàng.
- Chọn HOME_PICKUP hoặc DROP_OFF.
- Chọn bưu cục nếu cần.

Bước 2: Thông tin người nhận.

- Họ và tên.
- Số điện thoại.
- Địa chỉ.

Bước 3: Hàng hóa.

- Danh sách mặt hàng.
- Cho phép thêm hoặc bỏ dòng hàng.
- Số lượng.
- Giá trị khai báo.
- Khối lượng.
- Kích thước.
- Ghi chú.

Bước 4: Xác nhận và thanh toán.

- Gọi API tính phí.
- Hiển thị tổng phí vận chuyển.
- Chọn VNPAY hoặc CASH.
- Kiểm tra lại toàn bộ thông tin.
- Xác nhận tạo vận đơn.

Customer được quay lại các bước trước để chỉnh sửa form trước khi tạo thành công.

### 13.6. Trang quản lý vận đơn

Sử dụng data table.

Hiển thị:

- Mã vận đơn.
- Người nhận.
- Thời gian tạo.
- Phí vận chuyển.
- Phương thức thanh toán.
- Trạng thái thanh toán.
- Trạng thái vận chuyển.
- Thao tác xem chi tiết.

Hỗ trợ:

- Tìm kiếm.
- Lọc.
- Phân trang.
- Xem chi tiết.
- Hủy vận đơn đủ điều kiện.

Không có thao tác chỉnh sửa hoặc xóa vận đơn.

### 13.7. Trang chi tiết vận đơn

Hiển thị:

- Thông tin chung.
- Thông tin người gửi.
- Thông tin người nhận.
- Danh sách hàng hóa.
- Khối lượng và kích thước.
- Phí vận chuyển.
- Trạng thái thanh toán.
- Trạng thái vận chuyển.
- Timeline hành trình.
- Kết quả giao hàng.
- Kết quả hoàn tiền nếu có.
- Nút hủy nếu đủ điều kiện.
- Nút thanh toán lại nếu đủ điều kiện.

### 13.8. Trang thanh toán

Hiển thị lịch sử giao dịch thuộc khách hàng.

Bao gồm:

- Mã giao dịch.
- Mã vận đơn.
- Phương thức.
- Số tiền.
- Trạng thái.
- Thời gian thanh toán.

### 13.9. Trang hoàn tiền

Hiển thị:

- Mã vận đơn.
- Mã giao dịch.
- Số tiền hoàn.
- Trạng thái hoàn tiền.
- Thời gian yêu cầu.
- Thời gian hoàn thành nếu có.

### 13.10. UI/UX Requirements

- Responsive.
- Layout nhất quán.
- Reusable components.
- Route Guard.
- Lazy loading theo route nếu phù hợp.
- Loading state.
- Skeleton loading.
- Empty state.
- Error state.
- Toast notification.
- Confirmation dialog.
- Form validation.
- Accessible form controls.
- Status badge.
- Pagination.
- Không sử dụng quá nhiều animation.
- Không xây dựng giao diện Admin hoặc Shipper.

## 14. Frontend Routes

Các route đề xuất:

Public:

- /
- /tracking
- /tracking/:trackingCode
- /login
- /register

Customer:

- /dashboard
- /dashboard/shipments
- /dashboard/shipments/create
- /dashboard/shipments/:id
- /dashboard/payments
- /dashboard/refunds
- /dashboard/profile
- /dashboard/change-password

Payment Result:

- Route hiển thị kết quả VNPay, xác định theo flow tích hợp.

Các route Customer yêu cầu xác thực.

Frontend Route Guard chỉ phục vụ điều hướng và trải nghiệm người dùng.

Backend vẫn phải kiểm tra quyền đối với mọi request.

## 15. Backend Architecture

Áp dụng Package by Feature.

Các package nghiệp vụ:

- auth
- user
- shipment
- payment
- refund
- dashboard
- tracking
- integration
- security
- config
- common

Trong mỗi feature có thể tổ chức:

- controller
- service
- repository
- entity
- dto
- mapper
- validation

Integration package chứa adapter của:

- shipping
- pricing
- warehouse
- vnpay

Không đặt business logic quan trọng trong Controller.

Không trả trực tiếp JPA Entity nếu làm lộ dữ liệu nội bộ.

Sử dụng DTO và validation.

Xây dựng Global Exception Handler.

Thống nhất API response và error response.

Hỗ trợ pagination.

Cung cấp Swagger/OpenAPI.

## 16. Frontend Architecture

Áp dụng Feature-based Architecture.

Cấu trúc dự kiến:

- src/app
- src/layouts
- src/features/home
- src/features/auth
- src/features/tracking
- src/features/dashboard
- src/features/shipments
- src/features/payments
- src/features/refunds
- src/features/profile
- src/components/ui
- src/components/shared
- src/hooks
- src/lib
- src/types
- src/assets
- src/styles

Sử dụng:

- Axios cho HTTP Client.
- TanStack Query cho server state.
- React Hook Form và Zod cho form validation.
- React Router cho routing.
- shadcn/ui cho component.
- Recharts cho biểu đồ.

Không sao chép business logic tính phí hoặc trạng thái vận chuyển vào Frontend.

Không lưu secret trên Frontend.

Backend URL được cấu hình thông qua biến môi trường.

## 17. API Requirements

### 17.1. Customer Public API

Các nhóm API cần có:

- Register.
- Login.
- Refresh session nếu áp dụng.
- Public tracking.
- VNPay callback/IPN theo cơ chế tích hợp.

### 17.2. Customer Authenticated API

- Get profile.
- Update profile.
- Change password.
- Create shipment.
- Get shipment list.
- Get shipment detail.
- Request shipment cancellation.
- Request shipping fee.
- Get available drop-off locations.
- Create VNPay payment.
- Query payment result.
- Get payment history.
- Get refund history.
- Get dashboard statistics.

### 17.3. Internal API

- Create counter shipment.
- Confirm cash payment.

Chỉ cho phép các service hoặc nhân viên được xác thực phù hợp truy cập.

### 17.4. External Integration API

Các API cần sử dụng từ service khác:

- Tính phí vận chuyển.
- Lấy danh sách bưu cục.
- Tiếp nhận vận đơn mới.
- Lấy trạng thái vận đơn.
- Lấy lịch sử hành trình.
- Lấy kết quả giao hàng.
- Hủy vận đơn.
- Lấy dữ liệu trạng thái hàng loạt hoặc thống kê nếu được hỗ trợ.
- Nhận hoặc cung cấp thông tin thanh toán phục vụ phối hợp nghiệp vụ.

Không mặc định các endpoint này thuộc cùng một service.

Không tự quyết định endpoint, field, status code nghiệp vụ của service khác.

API Contract phải được thống nhất với các thành viên phụ trách.

### 17.5. Integration Reliability

Cần xử lý:

- Connection timeout.
- Read timeout.
- External API unavailable.
- Invalid response.
- Authentication failure.
- Duplicate requests.
- Idempotency.
- Partial failure.
- Response mapping.

Đặc biệt với tạo vận đơn:

Nếu Customer Database đã lưu thành công nhưng service vận chuyển chưa xác nhận tiếp nhận, không được mất dữ liệu hoặc tạo vận đơn trùng.

Thiết kế cơ chế theo dõi kết quả đồng bộ và retry an toàn ở mức đơn giản phù hợp đồ án.

Không giữ database transaction mở trong thời gian gọi HTTP dài.

Không giả định database transaction của Spring Boot có thể rollback dữ liệu đã cập nhật tại service Go.

Không triển khai distributed transaction hoặc message broker phức tạp nếu chưa cần thiết.

## 18. Security Requirements

- Spring Security.
- JWT Authentication.
- Hash mật khẩu bằng thuật toán phù hợp.
- Kiểm tra quyền sở hữu vận đơn.
- Kiểm tra quyền sở hữu thanh toán và hoàn tiền.
- Phân biệt Public API, Customer API và Internal API.
- Không cho phép đăng ký ROLE đặc quyền.
- Kiểm tra trạng thái tài khoản.
- Không tin tưởng customer_id do frontend tự cung cấp.
- Không tin tưởng số tiền do frontend tự cung cấp.
- Không cho phép giả mạo xác nhận thanh toán.
- Xác minh VNPay callback.
- Input validation.
- Rate limiting cho API tra cứu và xác thực.
- CORS được cấu hình đúng.
- Xử lý XSS và CSRF phù hợp với phương thức lưu token.
- Không đưa secret vào repository.
- Không log mật khẩu, token, secret hoặc dữ liệu cá nhân không cần thiết.
- Sử dụng HTTPS khi triển khai thực tế.

Cơ chế đã thống nhất cho Customer:

- Access token JWT có thời hạn 24 giờ, lưu trong bộ nhớ Frontend.
- Refresh token có thời hạn 7 ngày, lưu bằng cookie HttpOnly; bật Secure khi triển khai HTTPS và cấu hình SameSite phù hợp cách triển khai.
- Backend lưu hash refresh token và thông tin hiệu lực phiên trên users; không lưu token gốc. Mỗi tài khoản chỉ duy trì một phiên, đăng nhập mới thay thế phiên cũ.
- Xoay refresh token khi refresh. Đăng xuất hoặc đổi mật khẩu thu hồi phiên; Backend kiểm tra tài khoản và phiên còn hiệu lực trên mỗi request được xác thực để access token cũ cũng bị từ chối.
- Bảo vệ endpoint dùng cookie bằng kiểm tra Origin và cơ chế CSRF phù hợp; cấu hình CORS theo danh sách origin được phép.
- API nội bộ Customer dự kiến dùng API key riêng cho từng service, đọc từ biến môi trường, phân quyền theo thao tác và tách biệt với JWT Customer. Cần thống nhất với các thành viên gọi API trước khi tích hợp thật.
- Cơ chế xác thực khi Customer gọi service bên ngoài tuân theo contract được nhóm thống nhất; không mặc định các service khác đã dùng API key.

## 19. Testing Requirements

Áp dụng TDD cho các nghiệp vụ Backend quan trọng bằng JUnit 5 và Mockito. Frontend được kiểm thử theo quy trình riêng; không cài đặt framework kiểm thử Frontend hoặc tự viết bộ test tự động Frontend trong phạm vi triển khai này.

### 19.0. Quy tắc cách ly mã kiểm thử (bắt buộc)

- Toàn bộ mã kiểm thử phải nằm trong thư mục test/ độc lập tại root repository, ngang cấp customer-service/ và customer-web/.
- Không đặt test trong customer-service/src/test/, customer-service/src/main/, customer-web/src/ hoặc bất kỳ thư mục source code chính nào.
- test/backend/ chứa module Maven kiểm thử riêng, có pom.xml riêng, phụ thuộc vào artifact của Customer Backend. JUnit 5, Mockito và các dependency chỉ phục vụ kiểm thử được khai báo tại module này, không khai báo trong pom.xml của ứng dụng chính.
- Có thể phân chia unit test và integration test bên trong test/backend/. Mọi fixture, mock, stub, fake, test helper, test configuration, test resource, dữ liệu mẫu kiểm thử và script kiểm thử phải nằm trong test/.
- Không thêm test hook, test-only constructor/method, endpoint dành riêng cho test, cờ bypass, logic phân nhánh theo môi trường test hoặc cấu hình phục vụ riêng cho test vào mã nguồn chính.
- Không thay đổi access modifier, làm lộ trạng thái nội bộ hoặc thêm abstraction chỉ để test dễ viết. Với những hàm, class có  access modifie là private, protected có thể sử dụng conflection để test. Được sử dụng dependency injection, interface và các thiết kế cần thiết cho nghiệp vụ, bảo trì và tích hợp thực tế.
- Cần có javadoc ở trên mỗi hàm test để mô tả hoạt động cho test case đó. quy ước đắt tên: Test_tên hàm cần test_số tăng dần từ 001
- Module kiểm thử được phép phụ thuộc vào ứng dụng chính; ứng dụng chính tuyệt đối không phụ thuộc ngược lại vào module kiểm thử. Artifact và Docker image triển khai không chứa mã hoặc tài nguyên kiểm thử.
- Build ứng dụng chính và chạy kiểm thử bằng các lệnh riêng. Tài liệu README mô tả cách build/install artifact Backend trước khi chạy module test độc lập. Không chỉnh sửa source chính chỉ để chuẩn bị cho kiểm thử.
- Nếu kiểm thử phát hiện lỗi nghiệp vụ, sửa mã nguồn chính để khắc phục lỗi thực tế; không thêm workaround chỉ để test pass.

### 19.1. Authentication

- Đăng ký thành công.
- Email trùng.
- Số điện thoại trùng.
- Mật khẩu không hợp lệ.
- Đăng nhập thất bại.
- Tài khoản bị khóa.
- JWT không hợp lệ.
- Truy cập API không có quyền.

### 19.2. Shipment

- Tạo vận đơn HOME_PICKUP.
- Tạo vận đơn DROP_OFF.
- Tạo vận đơn tại bưu cục không có customer_id.
- Chặn tổ hợp creation_channel và pickup_method không hợp lệ.
- Một vận đơn chứa nhiều mặt hàng.
- Dữ liệu kiện hàng không hợp lệ.
- API tính phí thành công.
- API tính phí thất bại.
- Số tiền bị chỉnh sửa từ Frontend.
- Mã vận đơn duy nhất.
- Lấy danh sách vận đơn đúng chủ sở hữu.
- Chặn truy cập vận đơn người khác.
- Không cho chỉnh sửa vận đơn.
- Xử lý lỗi khi đồng bộ service vận chuyển.
- Chống tạo vận đơn trùng.

### 19.3. Cancellation

- Hủy vận đơn thành công.
- Hủy tại chỗ khi vận đơn chắc chắn chưa từng gửi và chưa có worker giành quyền gửi.
- Hủy tại chỗ đồng thời với scheduler hoặc callback thanh toán không làm gửi đơn đã hủy.
- Không hủy tại chỗ khi tác vụ đang gửi, đã được giành quyền gửi nhưng bị gián đoạn hoặc có kết quả gửi chưa xác định.
- Hủy vận đơn không đủ điều kiện.
- Hủy vận đơn của người khác.
- API hủy bị timeout.
- API hủy trả kết quả chưa xác định.
- Hủy đơn nhiều lần.
- Không hoàn tiền khi hủy chưa thành công.

### 19.4. Payment

- Tạo giao dịch VNPay.
- Xác minh chữ ký hợp lệ.
- Callback có chữ ký không hợp lệ.
- Sai số tiền.
- Sai mã giao dịch.
- Callback trùng lặp.
- Thanh toán thất bại.
- Thanh toán lại hợp lệ.
- Chống thanh toán thành công nhiều lần.
- Xác nhận thanh toán tiền mặt từ service hợp lệ.
- Chặn xác nhận tiền mặt trái phép.
- Callback thành công đến sau khi vận đơn đã hủy.
- Callback thành công đến sau khi hủy tại chỗ tạo yêu cầu hoàn tiền và không kích hoạt tác vụ gửi đơn.

### 19.5. Refund

- Hoàn tiền toàn phần.
- Không hoàn tiền khi chưa thanh toán.
- Không hoàn tiền trùng.
- Không hoàn tiền nếu vận đơn chưa hủy thành công.
- Refund API thất bại.
- Refund API timeout.
- Refund đang xử lý.
- Kiểm tra kết quả trước khi retry.

### 19.6. Public Tracking

- Mã vận đơn hợp lệ.
- Mã không tồn tại.
- API vận chuyển không khả dụng.
- Không làm lộ dữ liệu nhạy cảm.
- Rate limiting.

### 19.7. Frontend — tiêu chí xác minh theo quy trình riêng

Các mục dưới đây là checklist nghiệm thu giao diện, không phải yêu cầu triển khai bộ test tự động Frontend.

- Route Guard.
- Form validation.
- Loading state.
- Error state.
- Empty state.
- Luồng tạo vận đơn.
- Luồng thanh toán.
- Luồng hủy và xem hoàn tiền.
- Responsive layout.

Sử dụng mock cho dependency ngoài phạm vi khi kiểm thử Backend, chỉ trong test/.

Đối với integration test cần database thật, sử dụng MySQL dành riêng cho kiểm thử, được khởi động hoặc cấu hình độc lập (có thể dùng Docker Compose riêng trong test/). Không sử dụng database phát triển hoặc production cho kiểm thử. Cấu hình và dữ liệu kiểm thử phải nằm trong test/.

Không tuyên bố hoàn thành chức năng khi chưa thực hiện verification phù hợp.

## 20. Project Structure

Cấu trúc repository đề xuất:

- customer-web/
- customer-service/
- test/
- test/backend/
- docs/
- docs/references/
- docs/superpowers/
- PROJECT_SPEC.md
- AGENTS.md
- README.md
- docker-compose.yml
- .env.example
- .gitignore

customer-web chứa React Frontend.

customer-service chứa Spring Boot Backend, chỉ chứa mã và cấu hình phục vụ ứng dụng thực tế.

test/ là thư mục kiểm thử độc lập duy nhất. Cấu trúc đề xuất:

```text
test/
  backend/
    pom.xml
    src/test/java/       # Unit test và integration test
    src/test/resources/  # Fixture và cấu hình kiểm thử
  scripts/               # Script kiểm thử nếu cần
  README.md              # Hướng dẫn chạy kiểm thử
```

Không đưa module test vào dependency hoặc artifact triển khai của customer-service. Giữ thư mục test/ ở root kể cả khi điều chỉnh cấu trúc workspace.

docs chứa tài liệu thiết kế, API Contract và hướng dẫn tích hợp.

Giữ nguyên cấu trúc workspace hiện có nếu dự án đã được khởi tạo hợp lý.

Không tự ý xóa hoặc thay đổi source code có sẵn.

## 21. Development Phases

### Phase 0 — Analysis and Design

- Kiểm tra workspace.
- Đọc PROJECT_SPEC.md.
- Phân tích ranh giới trách nhiệm.
- Xác nhận các quyết định còn thiếu.
- Hoàn thiện ERD.
- Thiết kế Authentication.
- Thiết kế tích hợp Microservices.
- Thiết kế luồng thanh toán.
- Thiết kế xử lý lỗi và idempotency.
- Phác thảo UI.
- Tạo design specification.

### Phase 1 — Project Setup

- Khởi tạo Spring Boot.
- Khởi tạo React.
- Cấu hình MySQL.
- Docker Compose.
- Flyway.
- Thiết lập module Maven kiểm thử Backend độc lập tại test/backend/, không bổ sung dependency hoặc cấu hình kiểm thử vào ứng dụng chính.
- Thiết lập cấu trúc package và feature.
- Cấu hình môi trường.

### Phase 2 — Authentication and User

- Database migration.
- Register.
- Login.
- JWT.
- Authorization.
- Profile.
- Change password.
- Kiểm thử.

### Phase 3 — Public Website

- PublicLayout.
- Landing page.
- Login page.
- Register page.
- Tracking UI.
- Responsive design.

### Phase 4 — Customer Dashboard Layout

- CustomerLayout.
- Sidebar.
- Header.
- Route Guard.
- Shared components.

### Phase 5 — Shipment Management

- Shipment entities.
- Shipment item entities.
- Create shipment API.
- Counter shipment API.
- Shipping fee integration.
- Warehouse integration.
- Shipping integration.
- Create shipment form.
- Shipment list.
- Shipment detail.
- Kiểm thử.

### Phase 6 — Payment

- Payment entities.
- VNPay Sandbox.
- Payment URL generation.
- Return URL.
- IPN.
- Transaction verification.
- Cash confirmation.
- Payment history.
- Kiểm thử.

### Phase 7 — Cancellation and Refund

- Cancellation API.
- Integration cancellation.
- Refund entities.
- VNPay Refund API.
- Refund reconciliation.
- Cancellation UI.
- Refund history.
- Kiểm thử.

### Phase 8 — Tracking

- Public tracking API.
- External status integration.
- Tracking timeline.
- Delivery result.
- Polling.
- Privacy protection.
- Kiểm thử.

### Phase 9 — Dashboard and Statistics

- Dashboard API.
- Customer statistics.
- Recharts.
- Recent shipments.
- Payment summary.
- Kiểm thử.

### Phase 10 — Integration and Finalization

- Tích hợp API thực tế với service Go.
- Tích hợp các service liên quan.
- Kiểm tra API Contract và kiểm thử tích hợp Backend trong test/.
- Xác minh end-to-end theo quy trình kiểm thử riêng, không tự bổ sung công cụ kiểm thử Frontend.
- Security review.
- Database review.
- Fix bugs.
- Hoàn thiện tài liệu.
- Hướng dẫn chạy bằng Docker Compose.

Thứ tự phase có thể điều chỉnh dựa trên dependency thực tế, nhưng không tự mở rộng phạm vi nghiệp vụ.

## 22. Acceptance Criteria

Dự án phải chạy được các kịch bản sau:

Scenario 1: Customer đăng ký, đăng nhập và tạo vận đơn lấy hàng tận nơi, thanh toán VNPay Sandbox, theo dõi vận đơn thông qua API.

Scenario 2: Customer tạo vận đơn online, chọn tự mang đến bưu cục và thanh toán tiền mặt.

Scenario 3: Service nhân viên tạo vận đơn hộ khách hàng không có tài khoản. Guest sử dụng mã vận đơn để tra cứu.

Scenario 4: Customer đã thanh toán VNPay, yêu cầu hủy vận đơn, service vận chuyển xác nhận hủy và hệ thống thực hiện hoàn tiền toàn bộ.

Scenario 5: Customer yêu cầu hủy nhưng service vận chuyển từ chối. Không thay đổi kết quả thành đã hủy và không hoàn tiền.

Scenario 6: Customer không thể truy cập vận đơn, thanh toán hoặc hoàn tiền của khách hàng khác.

Scenario 7: Service vận chuyển không khả dụng. Customer Website xử lý lỗi phù hợp, không tạo trạng thái giả hoặc làm mất dữ liệu.

Scenario 8: Callback thanh toán hoặc yêu cầu hoàn tiền bị gửi nhiều lần. Hệ thống không xử lý giao dịch tài chính trùng lặp.

Scenario 9: Guest truy cập website công khai trước khi đăng nhập và tra cứu vận đơn mà không làm lộ dữ liệu nhạy cảm.

## 23. Quy ước làm việc với AI Coding Agent

Sử dụng Superpowers đã được cài đặt trong môi trường phát triển.

Tuân thủ các workflow của skill thực tế đang được cài đặt.

Ưu tiên:

- Brainstorming.
- Design specification.
- Writing plans.
- Test-driven development cho Backend, tuân thủ quy tắc cách ly tại mục 19.0.
- Systematic debugging.
- Code review.
- Verification before completion.

Không bắt đầu viết toàn bộ code ngay sau khi đọc tài liệu.

Trước tiên:

1. Kiểm tra workspace.
2. Đọc các instruction và skill hiện có.
3. Đọc tài liệu dự án.
4. Xác nhận cách hiểu về phạm vi Customer.
5. Xác định những quyết định kỹ thuật còn thiếu.
6. Không hỏi lại những vấn đề nghiệp vụ đã được thống nhất trong tài liệu này.
7. Thảo luận những điểm thực sự cần quyết định.
8. Hoàn thiện design specification.
9. Tạo implementation plan.
10. Chỉ triển khai khi kế hoạch đã được thống nhất.

Không tự ý:

- Thay đổi technology stack.
- Cài đặt framework kiểm thử Frontend hoặc công cụ quản lý container dành cho kiểm thử tự động.
- Tạo mã, dependency, cấu hình hoặc tài nguyên kiểm thử trong ứng dụng chính.
- Thêm logic hoặc thay đổi thiết kế mã nguồn chính chỉ để phục vụ kiểm thử.
- Đặt mock/stub/fake phục vụ kiểm thử ngoài thư mục test/.
- Thêm ROLE Shop hoặc Shipper.
- Tạo Admin Dashboard.
- Thêm API Gateway.
- Xây dựng service vận chuyển.
- Xây dựng service kho.
- Tự định nghĩa trạng thái vận chuyển.
- Tự tính phí vận chuyển.
- Thêm COD.
- Thêm Order hoặc Product.
- Chuyển kiến trúc Microservices thành hệ thống monolith toàn cục.

Các workflow Superpowers phải tuân thủ quy tắc cách ly kiểm thử tại mục 19.0. Khi thực hiện TDD, tạo test trong test/ trước, sau đó triển khai nghiệp vụ thật trong source chính và chạy lại test. Không tạo mã hỗ trợ riêng cho test trong ứng dụng. Nếu hướng dẫn của skill mặc định đặt test cạnh source code, điều chỉnh vị trí test theo đặc tả này.

Trong quá trình triển khai, nếu phát hiện mâu thuẫn hoặc vấn đề kỹ thuật, cần giải thích và đề xuất giải pháp trước khi thực hiện thay đổi lớn.

Mục tiêu cuối cùng là xây dựng một phân hệ Customer hoàn chỉnh, có thể phát triển độc lập và tích hợp với toàn bộ hệ thống Microservices của nhóm.
