# Microservice: Quản Lý Đơn Hàng & Quản Lý Kho (`manager-product-and-warehouse`)

Phân hệ Microservice phụ trách **2 đại Use Case** trong hệ thống **Quản lý vận đơn (DC-Post)** cho nhóm 3 thành viên:
1. **UC01 - Quản lý Đơn hàng:** Tạo đơn hàng (Khách hàng & Nhân viên), Thanh toán, Quản lý trạng thái đơn hàng & lịch sử trạng thái, Xem trạng thái / Hủy đơn hàng & Hoàn tiền, Quản lý vận đơn & Xem lịch sử giao hàng.
2. **UC02 - Quản lý Kho:** Quản lý danh mục Kho & Sản phẩm, Quản lý hàng tồn kho (Nhập/Xuất/Giữ chỗ/Kiểm kê), Lịch sử hàng hóa ra/vào kho.

---

## Công Nghệ & Quy Ước Kiến Trúc Đã Chốt
* **Ngôn ngữ:** Golang (Go 1.24+) + Gin + `pgx/v5` + JWT HS256
* **Cơ sở dữ liệu:** PostgreSQL dùng chung giữa 3 Microservice (`dc_post`), **không tạo ràng buộc `FOREIGN KEY` / `CHECK` giữa các bảng ở tầng DB** (toàn bộ kiểm tra tham chiếu và nghiệp vụ xử lý tại tầng Go Usecase).
* **Bổ sung chuẩn hóa DB:** Bảng `orders` thêm cột `warehouse_id` để gắn kho xử lý đơn từ lúc tạo đơn (đảm bảo giữ chỗ/nhả tồn kho chính xác khi hủy đơn & xuất vận đơn).
* **Kiến trúc:** Clean Architecture (`domain`, `dto`, `repository/postgres`, `usecase`, `delivery/http`) gói gọn trong `manager-product-and-warehouse/`.

---

## Danh Mục Bộ Tài Liệu Dự Án (Chuẩn SDLC + Superpowers Review)

Toàn bộ tài liệu phân tích, thiết kế và kế hoạch triển khai nằm tại thư mục [`docs/`](./docs/):

1. **[01_SRS_Specification.md](./docs/01_SRS_Specification.md)** — **Tài liệu Đặc tả Yêu cầu Phần mềm (SRS):**
   - Phân chia phạm vi Microservice cho nhóm 3 người.
   - Sơ đồ Use Case tổng quan sau khi gộp chuẩn hóa 2 cụm UC (`Quản lý đơn hàng` & `Quản lý kho`).
   - Đặc tả chi tiết Actor, Tiền điều kiện, Hậu điều kiện, Luồng sự kiện chính và Luồng ngoại lệ.
2. **[02_System_And_Database_Design.md](./docs/02_System_And_Database_Design.md)** — **Tài liệu Thiết kế Hệ thống & Cơ sở dữ liệu (SDD):**
   - Kiến trúc Microservice dùng chung PostgreSQL (không FK giữa các bảng) & hợp đồng JWT.
   - 3 sơ đồ Sequence Diagram chi tiết (Tạo đơn & giữ kho, Tạo vận đơn & xuất kho, Hủy đơn & hoàn tiền).
   - Bảng State Machine & Single Source of Truth cho API chuyển trạng thái.
   - Sơ đồ ER và Từ điển dữ liệu (Data Dictionary) chi tiết cho 15 bảng PostgreSQL + chiến lược Index.
3. **[03_Folder_And_Code_Architecture.md](./docs/03_Folder_And_Code_Architecture.md)** — **Tài liệu Cấu trúc Thư mục & Kiến trúc Mã nguồn:**
   - Cây thư mục chi tiết trong `manager-product-and-warehouse`.
   - Quy tắc phụ thuộc (Dependency Rule) giữa các tầng `domain`, `dto`, `repository`, `usecase`, `delivery`.
   - Mẫu quản lý Database Transaction (`TxManager`) và quy chuẩn đặt tên.
4. **[04_API_Specification.md](./docs/04_API_Specification.md)** — **Tài liệu Đặc tả RESTful API:**
   - Chuẩn JSON Request/Response, Phân trang, Phân quyền theo Role (`CUSTOMER`, `STAFF` theo `warehouse_staff`, `ADMIN`).
   - Đặc tả đầy đủ các endpoints của UC01 & UC02 kèm Payload mẫu và Bảng mã lỗi chuẩn.
5. **[05_Implementation_Plan.md](./docs/05_Implementation_Plan.md)** — **Kế Hoạch Triển Khai Chi Tiết (Superpowers Implementation Plan):**
   - Global Constraints, 5 điểm Review Focus trọng yếu, và 6 Task TDD độc lập theo từng giai đoạn để sẵn sàng chuyển sang code bất cứ lúc nào.
