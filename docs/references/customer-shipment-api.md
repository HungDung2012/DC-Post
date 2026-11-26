# Customer shipment API

Cập nhật 26/11/2026, Task 14. Các endpoint này thuộc Customer Service, yêu cầu
`Authorization: Bearer <accessToken>` của Customer còn hiệu lực. Đây không phải API
Contract của backend Go. Các yêu cầu không xác thực trả 401.

## Tạo vận đơn online

`POST /api/v1/customer/shipments`, `Content-Type: application/json`.
Header `Idempotency-Key` bắt buộc: 1–128 ký tự ASCII hiển thị, không khoảng trắng,
phân biệt hoa thường. Body là object shipment trực tiếp, gồm sender, recipient,
parcel, items, pickupMethod, dropOffWarehouseId, paymentMethod, confirmedFee, note.
Chi tiết trường snapshot/parcel/items theo [dữ liệu tạo đơn](customer-counter-api.md).
Online chấp nhận HOME_PICKUP hoặc DROP_OFF; DROP_OFF yêu cầu ID bưu cục INT dương
được Warehouse adapter xác minh. HOME_PICKUP không có dropOffWarehouseId.

Backend lấy customer/actor từ JWT, đặt creationChannel=ONLINE; không nhận quyền sở
hữu, actor hay channel do payload khai báo. Pricing adapter xác minh lại confirmedFee.
Tạo thành công hoặc replay cùng customer/key/nội dung đã chuẩn hóa trả HTTP 201:
shipmentId, trackingCode, shippingFee, paymentMethod, syncStatus. Cùng key khác nội
dung trả 409 IDEMPOTENCY_CONFLICT; phí thay đổi trả 409 FEE_CHANGED và currentFee.
Dependency lỗi trả 502/503, không tự sinh phí. Replay bản ghi hợp lệ không gọi lại dependency.

CASH bắt đầu PENDING, VNPAY bắt đầu WAITING_PAYMENT. Tạo đơn không gọi Shipping
hoặc xác nhận đã thanh toán. Không có PUT/PATCH/DELETE sửa vận đơn.

## Danh sách vận đơn

`GET /api/v1/customer/shipments`.

| Query | Quy tắc |
|---|---|
| page | Mặc định 0; số nguyên 0–1.000.000 |
| size | Mặc định 20; số nguyên 1–100 |
| q | Tìm chuỗi con trackingCode, tên hoặc điện thoại người nhận; strip; tối đa 500 ký tự; %, _ và ! là ký tự thường |
| paymentMethod | CASH hoặc VNPAY |
| syncStatus | WAITING_PAYMENT, PENDING, PROCESSING, SYNCED, UNKNOWN, BLOCKED, CANCELLED; đây là trạng thái đồng bộ kỹ thuật |
| createdFrom | Instant ISO-8601; cận dưới bao gồm |
| createdBefore | Instant ISO-8601; cận trên không bao gồm |
| paymentStatus, shippingStatus | Chưa khả dụng; truyền vào nhận 400 FILTER_UNAVAILABLE |
| sort | Không hỗ trợ tùy chỉnh; truyền vào nhận 400 INVALID_SHIPMENT_QUERY |

Hai cận ngày phải trong khoảng UTC `1000-01-01T00:00:00Z` đến
`9999-12-31T23:59:59.999999Z`; createdFrom phải nhỏ hơn createdBefore khi có cả hai.
Thứ tự cố định createdAt giảm dần, shipmentId giảm dần để xử lý thời gian trùng nhau.
Mọi điều kiện và count đều giới hạn customer trong SQL trước phân trang; không gọi
Shipping cho danh sách hoặc lọc dữ liệu ngoài sau phân trang.

Response HTTP 200 gồm content, page, size, totalElements, totalPages, capabilities.
Mỗi dòng content gồm shipmentId, trackingCode, recipientName, recipientPhone,
shippingFee, paymentMethod, pickupMethod, syncStatus, cancellationStatus, createdAt.
Trang ngoài dữ liệu trả content rỗng. capabilities hiện là:

```json
{"paymentMethodFilter":true,"paymentStatusFilter":false,"shippingStatusFilter":false}
```

Chưa có bảng/dữ liệu payment tại Task 14; không suy ra PAID từ syncStatus. Bộ lọc
paymentStatus cần hoàn thiện sau Task 19–24, dùng dữ liệu thanh toán local. Shipping
chưa có batch/filter capability nên Frontend cần ẩn bộ lọc shippingStatus.

## Chi tiết vận đơn

`GET /api/v1/customer/shipments/{id}`. ID không tồn tại hoặc của customer khác đều
trả HTTP 404 SHIPMENT_NOT_FOUND. Query customerId không thay đổi quyền truy cập.

Response HTTP 200 gồm shipment và tracking. shipment là snapshot local: shipmentId,
trackingCode, sender, recipient, parcel, items, shippingFee, paymentMethod, pickupMethod,
dropOffWarehouseId, creationChannel, note, createdAt, syncStatus, cancellationStatus,
cancelReason, cancelledAt. Mỗi item có itemId, name, category, quantity, declaredValue,
note. Không trả entity, request hash/key hoặc thông tin scheduler nội bộ.

tracking gồm availability, sourceStatus, updatedAt, events, errorCode. Mỗi event có
sourceStatus, occurredAt, area. Các mã sourceStatus giữ nguyên từ adapter, chưa phải
enum nghiệp vụ vận chuyển chính thức.

| availability | Ý nghĩa |
|---|---|
| NOT_READY | Chưa SYNCED; không gọi Shipping; events rỗng |
| AVAILABLE | Shipping trả tracking hợp lệ, đúng trackingCode |
| UNAVAILABLE | Dependency lỗi hoặc dữ liệu không hợp lệ; snapshot/items vẫn trả bình thường, events rỗng, errorCode EXTERNAL_<kind> |

Đọc snapshot/items trong transaction ngắn trước khi gọi Shipping; không giữ database
transaction khi HTTP. Không dùng privateDetails bên ngoài để thay snapshot người
nhận hoặc đưa vào response. Không retry mutation trong luồng đọc.

Lỗi truy vấn trả Problem Details có code/correlationId và thông báo an toàn;
INVALID_SHIPMENT_QUERY và FILTER_UNAVAILABLE trả 400. Giá trị enum/ngày/số sai định
dạng cũng trả 400. Không trả SQL, stack trace hoặc secret.
