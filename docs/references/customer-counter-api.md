# Customer internal counter API

Cập nhật 26/11/2026. Đây là API do Customer Service sở hữu; cách tích hợp và xác thực với bên gọi vẫn cần đối chiếu tại Task 32. Tài liệu này không định nghĩa API của backend Go hoặc nghiệp vụ nội bộ bưu cục.

## Tạo đơn tại quầy

`POST /api/v1/internal/shipments`, `Content-Type: application/json`.

| Header | Yêu cầu |
|---|---|
| X-API-Key | Khóa riêng của service được cấp quyền COUNTER_SHIPMENT_CREATE |
| Idempotency-Key | Bắt buộc, 1–128 ký tự ASCII hiển thị không chứa khoảng trắng; phân biệt hoa thường |

Không dùng JWT Customer thay API key. Khóa API chỉ dùng ở backend của service gọi, không đưa vào trình duyệt. Service mặc định `counter` lấy khóa từ INTERNAL_COUNTER_API_KEY; khóa trống vô hiệu hóa truy cập. Không dùng chung khóa giữa các service.

Body JSON:

| Trường | Kiểu và quy tắc |
|---|---|
| createdBy | Số nguyên JSON dương, trong miền INT; ID nhân viên do service tin cậy cung cấp |
| customerId | Bỏ qua trường này hoặc truyền null. Giá trị khác null bị từ chối khi chưa có cơ chế xác minh liên kết tài khoản |
| shipment | Dữ liệu vận đơn theo bảng dưới đây |

Trường trong `shipment` dùng cùng dữ liệu nghiệp vụ với ONLINE:

| Trường | Quy tắc |
|---|---|
| sender, recipient | Mỗi object có name, phone, address; bắt buộc; lưu snapshot |
| parcel | weightKg, lengthCm, widthCm, heightCm dương; BigDecimal theo độ chính xác schema |
| items | Danh sách không rỗng; mỗi dòng có name, category, quantity nguyên dương, declaredValue không âm, note tùy chọn |
| pickupMethod | Chỉ DROP_OFF |
| dropOffWarehouseId | INT dương, bắt buộc và phải khớp danh sách Warehouse qua adapter |
| paymentMethod | CASH hoặc VNPAY; chỉ ghi phương thức chọn, không xác nhận đã thanh toán |
| confirmedFee | Phí VND nguyên không âm mà khách đã xác nhận; backend gọi Pricing xác minh lại |
| note | Ghi chú tùy chọn |

`creation_channel=COUNTER`, `customer_id=NULL` và `created_by_source` được backend xác định từ luồng gọi và service principal. Không lấy nguồn service từ payload; không tự ghép khách hàng theo phone/email và không tạo tài khoản Guest.

Phản hồi thành công HTTP 201, gồm shipmentId, trackingCode, shippingFee, paymentMethod, syncStatus. CASH bắt đầu PENDING; VNPAY bắt đầu WAITING_PAYMENT. Tạo đơn chưa đồng nghĩa dịch vụ vận chuyển đã nhận đơn hoặc khách đã thanh toán.

Cùng service/key/nội dung đã chuẩn hóa trả lại đơn đã lưu (vẫn HTTP 201), kể cả Pricing/Warehouse đang lỗi. Hash bao gồm createdBy và dữ liệu shipment; đổi nhân viên hoặc nội dung phải dùng khóa mới sau khi xử lý conflict. Hai service có thể dùng cùng key mà không truy cập bản ghi của nhau.

| Trường hợp | HTTP/kết quả |
|---|---|
| Không có hoặc sai API key; chỉ có JWT Customer | 401 |
| Service thiếu quyền tạo counter | 403 |
| Thiếu key, dữ liệu sai, HOME_PICKUP, customerId khác null, ID vượt INT hoặc có phần thập phân | 400 |
| Pricing trả phí khác confirmedFee | 409, code FEE_CHANGED, currentFee; không lưu và yêu cầu xác nhận lại |
| Cùng key nhưng khác nhân viên/nội dung | 409, code IDEMPOTENCY_CONFLICT |
| Dependency tạm ngừng | 503; không tạo phí giả |
| Dependency trả dữ liệu không hợp lệ | 502 |

JSON không tự cắt số thập phân thành Integer. Chuẩn hóa điện thoại +84 thành 0 và loại dấu phân cách; chuỗi được strip, ghi chú trắng thành null; decimal tương đương có cùng hash. Giữ thứ tự khai báo items.

## Phân quyền CASH

COUNTER_SHIPMENT_CREATE và CASH_PAYMENT_CONFIRM là hai quyền riêng. Tạo CASH không ghi nhận PAID. Khóa mặc định `cashier` chỉ có CASH_PAYMENT_CONFIRM nên không tạo counter; khóa `counter` chỉ có COUNTER_SHIPMENT_CREATE nên không xác nhận tiền mặt. Endpoint thu tiền thực tế được triển khai ở task thanh toán tương ứng; Task 13 chỉ bảo toàn ranh giới quyền này.

ID bưu cục/nhân viên hiện tạm INT theo chủ dự án; chưa coi đó là kiểu ID chính thức của service khác. Đối chiếu lại trong Task 32. Không có FK xuyên service và không thay đổi schema tại Task 13.
