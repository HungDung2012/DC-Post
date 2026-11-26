# Đồng bộ vận đơn bằng MySQL scheduler

Cập nhật Task 16, 27/11/2026. Chỉ dùng các trường sẵn có trên shipments, không có
integration_tasks, bảng hàng đợi hoặc migration mới. Chạy trong tiến trình Java
Spring Boot, không cần Docker hay worker riêng.

## Luồng xử lý

1. Scheduler xử lý một lô phục hồi/đối soát trước, rồi một lô gửi mới. Mỗi lô đọc tối
   đa batch_size ID theo sync_next_attempt_at rồi shipment_id. Hai lô chạy tuần tự,
   không giữ transaction trong vòng lặp. Lỗi quét phục hồi không chặn lô gửi mới.
2. Mỗi đơn được claim bằng UPDATE có điều kiện trong transaction REQUIRES_NEW:
   PENDING, CASH, cancellation_status=NONE, cancelled_at=NULL, đã đến hạn và còn
   ngân sách attempt. UPDATE
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
| CONNECT_TIMEOUT, chắc chắn chưa gửi và còn đủ điều kiện retry | PENDING, có backoff | EXTERNAL_CONNECT_TIMEOUT |
| Read timeout, lỗi HTTP hoặc dữ liệu không hợp lệ | UNKNOWN, chờ đối soát | EXTERNAL_<failureKind> |
| Hết số lần gửi | BLOCKED | SYNC_ATTEMPTS_EXHAUSTED |
| Retry không còn đủ điều kiện CASH/chưa hủy | BLOCKED | SYNC_RETRY_NOT_ELIGIBLE |

Chỉ HttpConnectTimeoutException được adapter phân loại CONNECT_TIMEOUT mới cho
retry trực tiếp. Các lỗi transport khác giữ UNKNOWN; HTTP 500 không chứng minh
Shipping chưa lưu đơn. Backoff sau lần gửi thứ n là initial_delay × 2^(n−1), chặn
tại max_delay, tính từ lúc nhận kết quả. Không sleep hoặc giữ khóa trong lúc chờ.

## Phục hồi sau restart và đối soát

PROCESSING có sync_started_at rỗng hoặc quá processing_timeout chuyển UNKNOWN,
ghi SYNC_PROCESSING_INTERRUPTED rồi tra cứu findSubmission bằng tracking code.
Không đưa PROCESSING quá hạn thẳng về PENDING. Trạng thái nằm trong MySQL nên
tiến trình mới tiếp tục xử lý mà không cần hàng đợi trong bộ nhớ.

Mỗi lần tra cứu claim một lease trên UNKNOWN bằng UPDATE có điều kiện, đặt
sync_started_at và sync_next_attempt_at=now+processing_timeout; commit trước HTTP.
Trong thời hạn lease chỉ một worker được tra cứu. Lease hết hạn có thể claim lại;
kết quả ghi phải khớp UNKNOWN/attempt/startedAt nên worker cũ không ghi đè worker
mới. Tra cứu không tăng sync_attempt_count: đây là số lần submit, không phải số GET.

| Kết quả tra cứu | Hành vi |
|---|---|
| FOUND | SYNCED, kể cả đã hết ngân sách submit |
| UNKNOWN hoặc lỗi tạm thời | Giữ UNKNOWN, tra cứu lại sau reconciliation_delay |
| Response/status null | UNKNOWN, EXTERNAL_INVALID_RESPONSE, hẹn tra cứu lại |
| UNAUTHORIZED | BLOCKED, EXTERNAL_UNAUTHORIZED |
| UNSUPPORTED | BLOCKED, SHIPPING_LOOKUP_UNSUPPORTED |
| NOT_FOUND, chưa có bảo đảm retry an toàn | BLOCKED, SHIPPING_RETRY_UNSUPPORTED |
| NOT_FOUND, đã xác minh bảo đảm retry an toàn | Kiểm tra lại CASH/chưa hủy/ngân sách dưới khóa; đủ điều kiện mới PENDING có backoff |

ShippingClient.supportsSafeResubmission() mặc định false; HTTP adapter hiện tại
giữ false. Chỉ adapter đã xác minh NOT_FOUND có thẩm quyền và phía nhận chống trùng
theo khóa ổn định, kể cả request cũ còn đang chạy đến muộn, mới được trả true. Đây
là capability nội bộ Customer, không phải API Contract chính thức cho Go.

UNKNOWN được tra cứu theo khoảng nghỉ và giới hạn lô; số lần tra cứu chỉ đọc không
dùng ngân sách submit. BLOCKED không tự xử lý lại. Không sửa database thủ công để
retry kết quả chưa rõ. Chưa tuyên bố exactly-once xuyên service; contract thật và
khả năng chống trùng/đối soát của Shipping vẫn phải xác minh ở Task 32.

## Điều kiện VNPAY

Task 16 chưa có payment schema hay kết quả VNPay đã xác minh, nên candidate query
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
| SHIPMENT_SYNC_MAX_ATTEMPTS | 5 | Tổng số lần submit tối đa, kể cả lần đầu; 1–100 |
| SHIPMENT_SYNC_PROCESSING_TIMEOUT | 2m | Hạn PROCESSING và lease tra cứu |
| SHIPMENT_SYNC_RETRY_INITIAL_DELAY | 30s | Backoff ban đầu khi retry an toàn |
| SHIPMENT_SYNC_RETRY_MAX_DELAY | 15m | Trần backoff |
| SHIPMENT_SYNC_RECONCILIATION_DELAY | 1m | Khoảng nghỉ giữa các lần tra cứu chưa rõ |

Các duration phải từ 1 giây đến 1 ngày; retry_initial_delay không vượt retry_max_delay.
processing_timeout phải lớn hơn tổng connect_timeout và read_timeout của adapter;
cấu hình không hợp lệ bị từ chối khi khởi động. Mỗi chu kỳ có tối đa batch_size
tra cứu và batch_size submit, không phải một giới hạn chung cho cả hai lô.

Không chạy các lô chồng lên nhau trong một scheduler. Muốn tạm tắt gửi tự động thì
đặt SHIPMENT_SYNC_ENABLED=false trong cấu hình chạy rồi khởi động lại Java. Không
đổi trạng thái database thủ công để retry đơn chưa rõ kết quả.

Profile test nằm hoàn toàn trong test/ và tắt scheduler nền để tránh tác động fixture
của các integration test khác. Kiểm thử scheduler chủ động gọi runBatch hoặc dùng
context riêng xác minh cấu hình task. HTTP mock/loopback chỉ phục vụ development/test,
không phải contract Go chính thức.
