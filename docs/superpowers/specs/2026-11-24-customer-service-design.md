# Customer Service — Design Specification

Ngày: 24/11/2026. Trạng thái: design và implementation plan đã được chủ dự án duyệt; đang triển khai, đã xác minh checkpoint Tasks 01–09.

Nguồn yêu cầu: `PROJECT_SPEC.md`. Bản tổng hợp và các chi tiết kỹ thuật đã được chủ dự án duyệt để lập kế hoạch; các điều kiện phụ thuộc contract tại mục 13 vẫn phải được xác minh trước tích hợp thật. Tài liệu này không phải API Contract chính thức của service Go. Chỉ bắt đầu triển khai sau khi implementation plan được thống nhất.

## 1. Phạm vi và các quyết định đã thống nhất

- Chỉ triển khai Customer Frontend, Backend, Database, tích hợp REST và VNPay Sandbox.
- Giữ nguyên technology stack, package by feature, feature-based Frontend và hai layout trong đặc tả gốc.
- Không triển khai Admin, Shipper, Warehouse, COD, Pricing hoặc quy trình vận chuyển. Không thêm API Gateway, message broker, Redis hoặc distributed transaction.
- Giữ 7 bảng: `users`, `roles`, `user_roles`, `shipments`, `shipment_items`, `payments`, `refunds`. Bổ sung trường có mục đích cụ thể, không thêm 4 bảng kỹ thuật từng được đề xuất.
- JWT access token 24 giờ trong bộ nhớ Frontend; refresh token 7 ngày trong cookie HttpOnly. Một phiên trên mỗi tài khoản.
- API nội bộ Customer dự kiến dùng API key riêng từng service và phân quyền theo thao tác. Cần xác nhận với phía gọi trước khi tích hợp thật.
- Scheduler đọc trực tiếp bảng nghiệp vụ, lưu trạng thái xử lý trong MySQL và gọi HTTP ngoài transaction.
- CASH được đồng bộ sau khi tạo; VNPAY chỉ được đồng bộ sau khi xác minh thanh toán thành công.
- Đơn chắc chắn chưa từng gửi được phép hủy tại Customer; đơn đã gửi hoặc chưa rõ kết quả phải theo quy trình đối soát/hủy bên ngoài.
- Đã thanh toán nhưng chưa đồng bộ: UI hiển thị “Đang xử lý — Đã thanh toán, đang đồng bộ vận đơn”; thanh toán vẫn là `PAID`.
- Hoàn tiền thất bại chỉ làm bản ghi hoàn tiền thành `FAILED`.

## 2. Kiến trúc và ranh giới

Frontend gọi Customer Backend. Backend sở hữu dữ liệu gốc và giao dịch trong MySQL; sử dụng Interface/Adapter để giao tiếp service ngoài. Frontend không gọi trực tiếp các service nội bộ.

| Thành phần | Trách nhiệm | Phụ thuộc chính |
|---|---|---|
| `auth`, `security` | Đăng ký, đăng nhập, phiên, kiểm tra quyền | `users`, JWT, cấu hình khóa |
| `user` | Hồ sơ và địa chỉ mặc định | `users` |
| `shipment` | Tạo đơn, snapshot, danh sách, hủy, đồng bộ | Repository, pricing/shipping/warehouse interface |
| `payment` | Lần thử thanh toán, CASH, xác minh VNPay | Repository, VNPay interface |
| `refund` | Hoàn toàn phần và đối soát | Thanh toán đã xác minh, kết quả hủy, VNPay interface |
| `tracking` | Dữ liệu theo dõi và DTO công khai | Shipping interface |
| `dashboard` | Thống kê đúng chủ sở hữu | Repository, dữ liệu tổng hợp bên ngoài nếu được hỗ trợ |
| `integration` | HTTP, xác thực phía ngoài, mapping và lỗi | Spring RestClient, cấu hình endpoint |

Controller chỉ điều phối HTTP, xác thực đầu vào và chuyển DTO. Nghiệp vụ, transaction và kiểm tra quyền sở hữu nằm tại service. Không trả JPA Entity trực tiếp ra API.

Interface biểu diễn năng lực Customer cần; endpoint và wire DTO chỉ được chốt khi có contract. Các cấu hình endpoint khác nhau được phép trỏ tới các service khác nhau.

## 3. Database: giữ 7 bảng

Quan hệ giữ nguyên: users N:N roles qua user_roles; users 1:N shipments (customer có thể NULL); shipments 1:N shipment_items và payments; payments 1:0..1 refunds.

Các trường gốc và kiểu dữ liệu tuân theo mục 12 của PROJECT_SPEC. Bổ sung kỹ thuật đề xuất theo từng nhu cầu:

| Bảng | Nhóm trường bổ sung | Mục đích |
|---|---|---|
| `users` | `refresh_token_hash`, `refresh_expires_at`, `auth_version` | Một phiên, xoay refresh và thu hồi JWT cũ |
| `shipments` | `payment_method` | Ghi phương thức được chọn ngay khi tạo, kể cả chưa có payment attempt |
| `shipments` | `creation_request_key`, `creation_request_hash`, `creation_request_scope`, `created_by_source` | Chống tạo trùng theo người gọi đã xác thực và phân biệt nguồn định danh người tạo |
| `shipments` | `sync_status`, `sync_attempt_count`, `sync_next_attempt_at`, `sync_started_at`, `sync_last_error_code` | Scheduler đồng bộ và nhận diện kết quả chưa rõ |
| `shipments` | `cancellation_status`, `cancellation_source`, `cancellation_request_key`, `cancellation_next_check_at` | Yêu cầu hủy, nguồn xác nhận và lịch đối soát |
| `payments` | Thời hạn thanh toán, thời điểm đối soát, thông tin giao dịch gốc VNPay còn thiếu | Không mở lần trả tiền mới khi lần cũ chưa rõ kết quả; đủ dữ liệu truy vấn/hoàn tiền |
| `refunds` | Thời điểm đối soát và metadata các yêu cầu gửi VNPay | Phân biệt yêu cầu hoàn tiền nghiệp vụ với lần gửi kỹ thuật |

Không thêm trường trùng dữ liệu chỉ để phục vụ hiển thị. Trường bổ sung phải được giải thích trong migration. Không lưu token gốc, secret hoặc nguyên callback chứa dữ liệu không cần thiết.

Thông tin bắt buộc của VNPay phải được đối chiếu tài liệu chính thức trước khi chốt migration payment/refund. Nếu retry cần request ID mới, phải giữ định danh cũ để đối soát; đề xuất lưu lịch sử metadata nhỏ trong một trường JSON trên `refunds`, chỉ gồm định danh, thời gian và kết quả cần thiết. Không tạo bộ máy audit chung. Đây là chi tiết đề xuất cần rà soát cùng tài liệu VNPay, không mặc định retry cần ID mới.

Constraint và index:

- `tracking_code`, `transaction_ref`, `refunds.payment_id` và `refunds.request_id` có unique constraint như đặc tả.
- Khóa tạo đơn có uniqueness theo nguồn yêu cầu và người gọi; với COUNTER không dựa vào `customer_id` NULL mà dùng định danh service đã xác thực. Khóa là bắt buộc khi tạo đơn để retry an toàn.
- Cùng khóa, cùng nội dung đã chuẩn hóa: trả đơn đã tạo; cùng khóa, khác nội dung: trả conflict. Kiểm tra quyền sở hữu trước khi trả dữ liệu.
- ONLINE bắt buộc customer; COUNTER chỉ DROP_OFF; DROP_OFF bắt buộc định danh bưu cục. Các ID bên ngoài chỉ là tham chiếu logic, không có FK xuyên service.
- Trường định danh bên ngoài hiện mô tả BIGINT trong đặc tả gốc; phải xác minh kiểu thực tế với nhóm trước migration liên quan. Không ép UUID hoặc chuỗi thành số.
- Index cho danh sách theo customer/thời gian và các điều kiện scheduler; không tạo index thứ hai trùng unique index.
- BigDecimal cho tiền và kích thước/khối lượng; VND không nhận số tiền có phần lẻ khi chuyển sang VNPay. Không tự làm tròn mức phí bên ngoài mà chưa có quy tắc được thống nhất.
- Thời gian lưu UTC; hiển thị Asia/Ho_Chi_Minh. Các giá trị thời gian ký/gửi VNPay tuân theo timezone của giao thức.

## 4. Authentication và API nội bộ

Đề xuất đăng nhập bằng email và mật khẩu; số điện thoại dùng làm thông tin liên hệ duy nhất. Chuẩn hóa email và điện thoại trước kiểm tra trùng. Mật khẩu được hash bằng BCrypt; role đăng ký luôn là CUSTOMER.

JWT chứa định danh user và `auth_version`. Mọi request Customer kiểm tra chữ ký, expiry, tài khoản hoạt động và phiên bản khớp database. Backend không tin role hoặc customer ID do request tự khai báo.

Đăng nhập mới tăng `auth_version`, lưu hash refresh token mới và thay phiên cũ. Refresh kiểm tra hash/thời hạn, xoay token nguyên tử và phát JWT mới cùng phiên bản. Refresh hết hạn yêu cầu đăng nhập lại. Đăng xuất/đổi mật khẩu tăng phiên bản và xóa dữ liệu refresh; access token cũ bị từ chối ngay. Không triển khai quản lý danh sách thiết bị.

Axios giữ access token trong bộ nhớ; khi tải lại trang gọi refresh. Chỉ một refresh chạy đồng thời trong một trang; request khác chờ kết quả. Refresh thất bại xóa trạng thái đăng nhập, không lặp vô hạn. TanStack Query xóa dữ liệu riêng tư khi đăng xuất hoặc thay tài khoản.

Refresh cookie có HttpOnly, thời hạn 7 ngày, Path giới hạn nhóm auth; HTTPS bật Secure. Môi trường chạy web/backend cùng site dùng SameSite=Lax. Nếu triển khai khác site thì phải rà soát lại cookie/CORS/CSRF trước khi đổi cấu hình. Các endpoint auth dùng cookie được kiểm tra Origin và CSRF token cho thao tác thay đổi phiên; CSRF token không phải refresh token và được phép đọc bởi Frontend.

API key nội bộ được map sang service principal và danh sách quyền trong cấu hình. JWT CUSTOMER không được dùng thay API key. Service tạo đơn tại quầy cung cấp định danh nhân viên theo quy ước đã thống nhất; Backend lưu kèm nguồn, không coi ID đó là khóa users của Customer. CASH chỉ được xác nhận từ service có quyền, đối chiếu số tiền backend và mã biên nhận chống trùng.

Rate limiting cho auth/public tracking dùng bộ nhớ trong tiến trình, phù hợp một instance của đồ án; không bảo đảm hạn mức toàn cục nếu nhân bản Backend. Giới hạn kích thước bộ nhớ, TTL và chỉ tin proxy header từ proxy đã cấu hình. Log không chứa key, token hoặc dữ liệu cá nhân không cần thiết.

## 5. Tạo vận đơn và xác nhận phí

1. Backend nhận dữ liệu đã validate, xác định customer từ principal; COUNTER lấy service principal riêng.
2. Lấy bưu cục/phí qua interface ngoài transaction. Nếu phí khác số tiền khách đã xem, trả yêu cầu xác nhận lại; không tạo đơn với phí mới âm thầm.
3. Trong transaction ngắn, kiểm tra idempotency, sinh tracking code khó đoán, lưu snapshot người gửi/nhận, mặt hàng và phí backend đã xác minh. Retry khi collision tracking code có giới hạn.
4. CASH ghi `sync_status=PENDING`; VNPAY ghi `sync_status=WAITING_PAYMENT`. Các tên này là trạng thái kỹ thuật nội bộ, không phải trạng thái vận chuyển.
5. Commit rồi trả đơn đã lưu. VNPAY chuyển sang bước thanh toán; CASH chờ scheduler.

Không triển khai cập nhật hoặc xóa vận đơn. Hồ sơ thay đổi không làm thay snapshot. Địa chỉ hiện lưu theo đặc tả gốc; nếu Pricing cần mã địa giới hoặc cấu trúc địa chỉ khác thì chỉ bổ sung sau khi xác minh contract, không tự dựng danh mục địa chỉ.

## 6. Scheduler đơn giản, không có bảng tác vụ

Một scheduler định kỳ xử lý theo lô nhỏ từ các bảng nghiệp vụ. Không giữ transaction trong suốt vòng lặp hoặc HTTP. Các chức năng được tách thành xử lý đồng bộ đơn, kiểm tra thanh toán, kiểm tra hủy và hoàn tiền; không xây framework hàng đợi tổng quát.

Đồng bộ đơn dùng các trạng thái kỹ thuật `WAITING_PAYMENT`, `PENDING`, `PROCESSING`, `SYNCED`, `UNKNOWN`, `BLOCKED`, `CANCELLED`. `CANCELLED` ở đây chỉ là việc gửi đã bị vô hiệu hóa, không đại diện trạng thái vận chuyển. `BLOCKED` biểu thị cần xử lý lỗi cấu hình/dữ liệu hoặc thiếu khả năng đối soát.

- Worker giành quyền bằng cập nhật có điều kiện trong transaction ngắn và commit `PROCESSING` trước HTTP. Một đơn chỉ có một worker giành quyền tại một thời điểm.
- Thành công: lưu `SYNCED` sau khi mapping response hợp lệ. Chỉ lỗi được xác định chắc chắn chưa gửi tới phía nhận mới có thể lên lịch gửi lại trực tiếp; lỗi xác thực/dữ liệu được giữ để xử lý nguyên nhân.
- Timeout, response không hợp lệ hoặc worker dừng sau khi giành quyền: giữ `UNKNOWN` hoặc chuyển sang `UNKNOWN` khi phát hiện `PROCESSING` quá hạn. Không tự coi hết hạn là chưa gửi.
- Chỉ retry thao tác có tác dụng phụ nếu contract bảo đảm idempotency hoặc đã đối soát được điều kiện gửi lại. Không tuyên bố exactly-once xuyên service khi phía nhận không hỗ trợ.
- Backoff có giới hạn và cấu hình được; không retry liên tục. Thời gian chờ HTTP, chu kỳ scheduler và hạn PROCESSING phải tương thích.
- Trước mỗi thao tác gửi mới, kiểm tra lại kết quả hủy và điều kiện thanh toán trong cùng cơ chế giành quyền.

Không trả dữ liệu giả hoặc chuyển sang trạng thái vận chuyển tự tạo khi service lỗi. Đã thanh toán nhưng đồng bộ chưa xong hiển thị đúng nhãn “Đang xử lý” đã thống nhất.

## 7. Payment và Refund

Khóa hàng `shipments` trong transaction ngắn khi mở payment attempt hoặc cập nhật kết quả liên quan để tuần tự hóa với hủy/đồng bộ. Không giữ khóa trong khi gọi VNPay. Một shipment có thể có nhiều attempt nhưng chỉ cho phép một attempt đang chờ/chưa rõ kết quả; chưa xác định thì truy vấn trước khi tạo attempt mới.

URL thanh toán lấy số tiền từ backend. Return URL chỉ điều hướng và hiển thị; IPN hoặc kết quả truy vấn được xác minh mới cập nhật trạng thái. Kiểm tra chữ ký, merchant, transaction reference, số tiền, tiền tệ và kết quả theo tài liệu VNPay chính thức. Frontend không gửi xác nhận PAID.

Callback trùng là thao tác idempotent. PAID không bị callback thất bại cũ ghi đè. IPN thành công và việc chuyển đơn sang chờ đồng bộ nằm trong cùng transaction, trừ khi đơn đã hủy. Không mở attempt mới khi một attempt chưa được kết luận an toàn. Không coi hết thời gian trên Frontend là bằng chứng VNPay chưa thu tiền.

Nếu VNPay báo một giao dịch thực tế ngoài dự kiến, phải ghi nhận và giữ để đối soát thay vì bỏ qua vì hệ thống đã có một PAID. Cơ chế chặn attempt mới làm giảm nguy cơ thu trùng; không thể phủ nhận kết quả thực tế phía nhà cung cấp.

Hoàn toàn phần chỉ khi đã PAID và hủy được xác nhận theo mục 8. Mỗi payment có tối đa một refunds record. Tạo refunds record trong transaction rồi gọi HTTP bên ngoài; scheduler có thể tiếp tục sau restart.

Worker hoàn tiền giành quyền bằng cập nhật nguyên tử từ PENDING sang PROCESSING trước HTTP; worker khác không được gửi cùng refund. PROCESSING bị gián đoạn phải đối soát trước khi gửi tiếp, không tự chuyển về PENDING khi hết thời gian chờ. Yêu cầu hủy và đối soát cũng phải tránh các lần gọi tác dụng phụ đồng thời trên cùng bản ghi.

`PENDING` là chờ gửi; `PROCESSING` là đã bắt đầu hoặc đang cần đối soát; `SUCCESS` chỉ khi có kết quả cuối cùng đã xác minh; `FAILED` chỉ khi xác định thất bại. Timeout không tự thành FAILED và không tạo thêm refund nghiệp vụ.

Phải xác minh Sandbox hỗ trợ Refund API và cách kiểm tra kết quả hoàn tiền thực tế. Không giả định API query giao dịch thanh toán cũng xác nhận được hoàn tiền. Khi chưa có phương thức đối soát đủ chắc chắn, giữ PROCESSING và hiển thị đang xử lý; không tự gửi lại hoặc báo thành công.

CASH không hoàn qua VNPay; chỉ ghi nhận thông tin và kết quả phối hợp trong phạm vi contract, không xây quy trình thu/hoàn tiền mặt của service khác.

## 8. Hủy và xử lý đồng thời

Customer chỉ yêu cầu hủy đơn thuộc mình. Trạng thái yêu cầu hủy nội bộ tách riêng với trạng thái vận chuyển; ghi nguồn xác nhận `LOCAL` hoặc `EXTERNAL`.

- Nếu đơn chắc chắn chưa từng được worker giành quyền gửi: khóa hàng, xác nhận hủy và vô hiệu hóa việc đồng bộ trong cùng transaction. Tạo refund nếu có PAID.
- Nếu worker đã giành quyền hoặc lần gửi chưa rõ kết quả: ghi nhận yêu cầu, chặn gửi mới và đối soát trước. Không hủy tại chỗ chỉ dựa trên việc chưa nhận response thành công.
- Nếu đã được service vận chuyển tiếp nhận: gửi yêu cầu hủy qua adapter; chỉ ghi nhận hủy thành công khi có xác nhận đúng contract.
- Kết quả hủy chưa rõ không cho phép hoàn tiền; kết quả từ chối giữ nguyên thực tế và không tạo refund.
- Callback PAID đến sau hủy: ghi nhận payment, tạo refunds record nếu chưa có và không kích hoạt lại đồng bộ.
- Payment callback, yêu cầu hủy và worker cùng dùng transaction ngắn/khóa theo shipment để không quyết định từ dữ liệu lỗi thời.

Không tự định nghĩa điều kiện hủy của service vận chuyển. Nếu không có khả năng đối soát trường hợp không rõ kết quả, giữ trạng thái xử lý và báo hạn chế tích hợp.

## 9. Tracking, danh sách và Dashboard

Public tracking dùng DTO allowlist riêng: tracking code, nhãn trạng thái nguồn ngoài, thời điểm cập nhật, sự kiện được phép công khai, khu vực gần đúng và kết quả giao hàng. Không trả trực tiếp payload ngoài; không công khai PII, tài chính hoặc thông tin nội bộ.

Danh sách đơn luôn query theo customer trước khi tổng hợp trạng thái. Nếu batch API có sẵn, gọi theo tập mã thuộc customer; không dùng dữ liệu của người khác. Không fan-out không giới hạn.

Lọc theo trạng thái vận chuyển chỉ bật khi API hỗ trợ truy vấn đủ để tính đúng tập kết quả và tổng số. Không lấy một trang local rồi lọc trạng thái trên trang đó. Khi API chưa hỗ trợ, UI thông báo bộ lọc chưa khả dụng; đây là điều kiện tích hợp đã cho phép trong đặc tả gốc.

Dashboard lấy tổng số đơn, biểu đồ tạo đơn và tài chính từ Customer Database. Số liệu theo trạng thái vận chuyển chỉ hiển thị khi có dữ liệu tổng hợp đầy đủ và mapping đã thống nhất. Khi thiếu dữ liệu hiển thị “Chưa thể cập nhật”, không dùng 0 hoặc hardcode thay thế.

Không lưu bảng trạng thái/hành trình trong Customer Database. Polling TanStack Query chỉ khi trang đang được xem, có khoảng chờ và dừng khi không cần; dữ liệu cũ nếu còn hiển thị phải có thời điểm cập nhật và chỉ báo lỗi.

## 10. Frontend và lỗi API

Giữ nguyên routes, PublicLayout, CustomerLayout và form tạo đơn 4 bước của PROJECT_SPEC. Không mở lại lựa chọn giao diện đã chốt. Public là website doanh nghiệp; Customer là dashboard cá nhân, không thêm giao diện nhân viên.

Payment return dùng route Customer `/dashboard/payments/result`; Backend return handler điều hướng bằng thông tin không nhạy cảm, không đưa secret/token vào URL. Nếu phiên không còn, đăng nhập rồi xem kết quả từ API có kiểm tra chủ sở hữu. Không tin query string để hiển thị thanh toán thành công.

Form giữ dữ liệu trong bộ nhớ khi chuyển bước, có validate bằng Zod. Thay trường ảnh hưởng phí làm phí cũ hết hiệu lực hiển thị; backend vẫn xác minh lại. Nút gửi bị khóa khi request đang chạy và giữ cùng idempotency key khi retry cùng một lần xác nhận. Lần gửi nội dung khác phải dùng khóa mới sau xử lý conflict thích hợp.

Mọi màn hình có loading, empty, error, responsive và điều khiển accessible. Nhãn vận chuyển lấy từ mapping dữ liệu ngoài; mã chưa biết hiển thị nhãn trung tính, không suy luận quy trình. Nhãn thanh toán/hoàn tiền/đồng bộ hiển thị riêng để khách không thanh toán lại nhầm.

Customer API dùng DTO và lỗi dạng Problem Details với `status`, `title`, `detail`, mã lỗi ứng dụng, lỗi từng trường khi có và correlation ID. Dùng HTTP status đúng nghĩa; phân biệt lỗi validate, chưa xác thực, không có quyền, conflict, giới hạn tần suất và dependency lỗi. Không trả stack trace hoặc nguyên lỗi chứa PII từ service ngoài.

Không tồn tại hoặc không thuộc quyền sở hữu trả cùng cách biểu diễn 404 cho tài nguyên riêng tư. Create trả tài nguyên đã được lưu, không tuyên bố phía vận chuyển đã nhận; thao tác đang chờ hoàn tất trả thông tin tiến trình rõ ràng.

## 11. Development adapter và môi trường

Source ứng dụng chỉ chứa interface nghiệp vụ và HTTP Adapter thực tế. Khi contract chưa có, adapter không được trình bày như đã tích hợp API Go chính thức.

Test mock/stub/fake và HTTP mock server development đều nằm trong `test/`. Ứng dụng gọi mock server qua endpoint configuration thông thường, không có mock profile, test bean hoặc nhánh fallback trong source chính. HTTP mô phỏng được ghi nhãn là tạm thời; chỉ thay mapping adapter khi contract thật được duyệt.

Docker Compose phục vụ Backend, Frontend và MySQL development; dữ liệu MySQL test chạy độc lập qua cấu hình trong `test/`. Không chia sẻ database test với development. Secret dùng biến môi trường; `.env.example` chỉ có placeholder tên biến. Các port/origin và phiên bản dependency được chốt trong plan setup dựa trên ma trận tương thích Java 21/Spring Boot/Springdoc và React/Vite/Tailwind; không đổi stack.

## 12. TDD, review và verification

Backend TDD theo RED → GREEN → REFACTOR cho nghiệp vụ quan trọng. Test và toàn bộ fixture/config/script nằm trong `test/`; module Maven `test/backend` phụ thuộc artifact Backend, ứng dụng không phụ thuộc ngược lại. Mỗi test có Javadoc và tên `Test_<methodName>_<sequence>` bắt đầu 001.

Backend xuất artifact JAR thông thường cho module test phụ thuộc và executable JAR riêng để chạy/deploy. Điều này phục vụ đóng gói/dependency, không thêm test hook hoặc dependency test vào ứng dụng. Build/install Backend trước, chạy test module bằng lệnh riêng.

Unit test dùng JUnit 5/Mockito chỉ trong module test. Integration test dùng MySQL riêng do Docker Compose trong `test/` khởi động độc lập; không thêm Testcontainers hoặc framework test Frontend.

Các ca trọng tâm: quyền sở hữu; phí bị sửa; idempotency cùng/khác payload; concurrent tạo đơn/payment/hủy; scheduler restart/timeout; giành quyền đồng thời; callback trùng/sai chữ ký/sai tiền; chặn thanh toán lại khi chưa rõ; PAID sau hủy; hủy local và external; refund timeout và không gửi trùng. Kiểm tra artifact/image không chứa tài nguyên test.

Frontend xác minh thủ công theo checklist mục 19.7, gồm toàn bộ các luồng thành công và lỗi; không tự tạo bộ test tự động Frontend. Backend contract/integration test và smoke test Sandbox không thay thế bằng unit test giả rồi tuyên bố đã tích hợp thật.

Code review tập trung vào bảo mật, quyền sở hữu, tính nguyên tử và trạng thái chưa xác định. Chạy build, test và verification phù hợp trước từng tuyên bố hoàn thành; lưu kết quả thực tế, báo rõ các ca chưa chạy vì dependency ngoài.

## 13. Điều kiện tích hợp cần xác nhận với nhóm

Các điểm sau là dependency bên ngoài cần giải quyết trước phần triển khai liên quan, không phải yêu cầu tự thiết kế service khác:

| Nội dung | Điều kiện sẵn sàng | Khi chưa có |
|---|---|---|
| Pricing và địa chỉ | Request/response, đơn vị, phí và cách xác minh lại | Interface và mock trong test; không tạo phí giả ở môi trường thật |
| Warehouse | Định danh bưu cục, danh sách và tính hợp lệ | Không tự xây database kho |
| Shipping | Tiếp nhận, định danh, idempotency, đối soát, hủy và xác thực | Không tự retry tác dụng phụ khi chưa bảo đảm an toàn |
| Trạng thái | Mapping, batch/filter/statistics và dữ liệu công khai | Tắt khả năng chưa được hỗ trợ và hiển thị thiếu dữ liệu |
| API nội bộ Customer | Danh tính service, quyền, nhân viên và biên nhận CASH | Không chấp nhận CUSTOMER token thay service authentication |
| VNPay Sandbox | Merchant, secret, URL IPN truy cập được, Refund API và phương thức đối soát | Test adapter trong test; không tuyên bố hoàn tiền thật thành công |

URL, tên field và business status của service ngoài không được xác lập trong tài liệu này. Trước tích hợp thật phải có contract review với thành viên phụ trách và cập nhật mapping/kiểm thử tương ứng.

## 14. Tiêu chí chấp nhận thiết kế và bước tiếp theo

Thiết kế đáp ứng các scenario tại mục 22, cùng ngoại lệ hủy local đã được xác nhận. Tính đúng của trạng thái tài chính được ưu tiên hơn việc tự kết luận một thao tác ngoài hệ thống đã hoàn tất.

Sau khi chủ dự án duyệt bản tài liệu này, dùng skill writing-plans để chia nhỏ các phase hiện có thành task có file liên quan, bước thực hiện, test RED/GREEN, lệnh verification và tiêu chí nghiệm thu. Các task tích hợp thật phải ghi rõ dependency contract. Chỉ bắt đầu code sau khi implementation plan được thống nhất.
