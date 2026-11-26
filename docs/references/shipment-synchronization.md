# Đồng bộ vận đơn bằng MySQL scheduler

Cập nhật Task 15, 26/11/2026. Chỉ dùng các trường sẵn có trên shipments, không có
integration_tasks, bảng hàng đợi hoặc migration mới. Chạy trong tiến trình Java
Spring Boot, không cần Docker hay worker riêng.

## Luồng xử lý

1. Scheduler đọc tối đa một lô ID đến hạn theo sync_next_attempt_at rồi shipment_id.
   Lô được xử lý tuần tự, không giữ transaction trong vòng lặp.
2. Mỗi đơn được claim bằng UPDATE có điều kiện trong transaction REQUIRES_NEW:
   PENDING, CASH, cancellation_status=NONE, cancelled_at=NULL, đã đến hạn. UPDATE
   đặt PROCESSING, timestamp theo microsecond và tăng attempt. Chỉ khi cập nhật đúng
   một hàng mới đọc snapshot/items và trả quyền gửi; transaction commit trước HTTP.
3. ShipmentSyncService không cho phép transaction từ caller. Nó gọi ShippingClient
   với snapshot đã đọc và khóa ổn định shipment:<trackingCode>. Không dùng request
   key tạo đơn của Customer làm khóa gửi ngoài.
4. Kết quả ghi trong transaction mới, chỉ khi shipmentId, PROCESSING, attempt và
   startedAt vẫn khớp. Response cũ không ghi đè attempt mới hoặc kết quả đã đối soát.

Điều kiện được kiểm tra lại tại UPDATE; kết quả chọn ID trước đó không cấp quyền
gửi. Hai worker có thể cùng chọn một ID nhưng chỉ một worker claim thành công.
Scheduler bỏ qua riêng một worker lỗi để tiếp tục các đơn khác. Log chỉ chứa ID và
tên loại lỗi, không payload, địa chỉ, token hoặc response từ dependency.

## Kết quả hiện tại

| Kết quả adapter | sync_status | sync_last_error_code |
|---|---|---|
| CONFIRMED | SYNCED | NULL |
| REJECTED | BLOCKED | SHIPPING_REJECTED |
| UNKNOWN | UNKNOWN | SHIPPING_UNKNOWN |
| Response hoặc outcome null | UNKNOWN | EXTERNAL_INVALID_RESPONSE |
| Lỗi xác thực dependency | BLOCKED | EXTERNAL_UNAUTHORIZED |
| Timeout, lỗi kết nối/HTTP hoặc dữ liệu không hợp lệ | UNKNOWN | EXTERNAL_<failureKind> |

UNKNOWN/BLOCKED không được claim lại. Nếu tiến trình dừng hoặc gặp lỗi nội bộ sau
claim, PROCESSING chưa được tự phục hồi tại Task 15. Task 16 sẽ xử lý restart,
đối soát, giới hạn attempt và backoff. Không tự chuyển về PENDING hoặc gửi lại mù
quáng. Khóa gửi ổn định chưa chứng minh exactly-once xuyên service; contract thật
và khả năng idempotency/đối soát của Shipping vẫn phải xác minh ở Task 32.

## Điều kiện VNPAY

Task 15 chưa có payment schema hay kết quả VNPay đã xác minh, nên candidate query
và claim đều chỉ nhận CASH. VNPAY luôn bị chặn, kể cả khi sync_status bị đổi nhầm
sang PENDING. Không tạo trường PAID giả hoặc thêm bảng sớm để lách dependency này.

Task 21 phải nối dữ liệu payment local đã xác minh PAID vào cả candidate query và
atomic claim, dưới cùng khóa/transaction shipment với callback và hủy. Chỉ chuyển
WAITING_PAYMENT sang PENDING chưa đủ khi claim còn giới hạn CASH. Cần kiểm thử
VNPAY đã PAID gửi một lần, chưa PAID không gửi và PAID sau hủy không gửi. Kết quả
tài chính PAID không bị biến thành FAILED khi Shipping lỗi; UI hiển thị đang xử lý
theo thiết kế đã duyệt. CASH không cần xác nhận đã thu trước khi đồng bộ.

## Cấu hình development

Các biến tùy chọn trong .env; giá trị mặc định:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| SHIPMENT_SYNC_ENABLED | true | Bật/tắt scheduler tự động |
| SHIPMENT_SYNC_BATCH_SIZE | 10 | Số ID tối đa/lô; 1–100 |
| SHIPMENT_SYNC_INTERVAL_MS | 10000 | Khoảng nghỉ sau khi lô kết thúc, milliseconds |
| SHIPMENT_SYNC_INITIAL_DELAY_MS | 10000 | Thời gian chờ sau startup trước lô đầu |

Không chạy các lô chồng lên nhau trong một scheduler. Muốn tạm tắt gửi tự động thì
đặt SHIPMENT_SYNC_ENABLED=false trong cấu hình chạy rồi khởi động lại Java. Không
đổi trạng thái database thủ công để retry đơn chưa rõ kết quả.

Profile test nằm hoàn toàn trong test/ và tắt scheduler nền để tránh tác động fixture
của các integration test khác. Kiểm thử scheduler chủ động gọi runBatch hoặc dùng
context riêng xác minh cấu hình task. HTTP mock/loopback chỉ phục vụ development/test,
không phải contract Go chính thức.
