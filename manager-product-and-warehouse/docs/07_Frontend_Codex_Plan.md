# Kế Hoạch & Checklist Frontend cho Codex (`manager-product-and-warehouse/frontend`)

## 1. Mục Tiêu & Phong Cách Thiết Kế (Logistics Control Tower)
Tham khảo trải nghiệm thực tế từ **ShipBob, Flexport, Giao Hàng Nhanh (GHN) và Viettel Post**:
- **Thẩm mỹ & Bố cục:** Giao diện SaaS vận tải & quản trị kho hiện đại, sáng sủa, tương phản cao (`slate-50` nền, `white` card viền mỏng `slate-200`, màu nhấn `indigo-600` cho hành động chính, bảng màu trạng thái ngữ nghĩa: `emerald` Thành công/Đã giao, `amber` Chờ xử lý/Tồn thấp, `sky` Đang giao, `rose` Hủy/Lỗi).
- **Cấu trúc khung (App Shell):**
  - Sidebar trái cố định (`DC-Post OMS & WMS`) với 6 mục điều hướng: **Tổng quan**, **Đơn hàng (UC01)**, **Vận đơn & Hành trình (UC01)**, **Kho & Nhân viên (UC02)**, **Sản phẩm (UC02)**, **Tồn kho & Lịch sử (UC02)**.
  - Topbar hiển thị trạng thái kết nối Backend (`/health`), và **Dev Role Switcher / JWT Auth Bar** cho phép chọn nhanh vai trò (`ADMIN #1`, `STAFF #22`, `CUSTOMER #105` — tự ký HS256 JWT bằng `VITE_DEV_JWT_SECRET` khớp `.env` của backend, hoặc dán JWT thật từ Auth Service).
- **Tech Stack:** React 19 + TypeScript + Vite + Tailwind CSS + `lucide-react` + `vitest` + `@testing-library/react`.

---

## 2. Kiến Trúc Docker Network (`dc-post-net`)
```text
Browser (http://localhost:3000)
   │
   ▼  Port forward 3000:80
[Container: frontend (Nginx Alpine)]
   │  Reverse Proxy nội bộ:
   │    /api/*   --> http://api:8082/api/*
   │    /health  --> http://api:8082/health
   ▼  Docker Network: dc-post-net
[Container: api (Go Gin :8082)]
   │
   ▼  Docker Network: dc-post-net
[Container: postgres (PostgreSQL 16 :5432)]
```

---

## 3. Phân Chia 4 Phần Thực Thi & Ranh Giới Cho Codex

| Phần | Model | Phạm vi & Files được phép tạo/sửa | Tiêu chí PASS trước khi sang phần tiếp |
| :--- | :---: | :--- | :--- |
| **FE Part 1**<br/>Scaffold, Design System, API Client, JWT Auth & App Shell | `gpt-6.2-sol` (`medium`) | `frontend/package.json`, `vite.config.ts`, `tsconfig.json`, `index.html`, `src/main.tsx`, `src/index.css`, `src/lib/jwt.ts`, `src/lib/api.ts`, `src/types/api.ts`, `src/context/AuthContext.tsx`, `src/components/Layout.tsx`, `src/components/ui/*` + unit tests (`jwt.test.ts`, `api.test.ts`, `Layout.test.tsx`) | `npm test` (`vitest run`) PASS 100%, `npm run build` PASS. Không sửa backend Go. |
| **FE Part 2**<br/>Dashboard & Toàn bộ màn hình UC02 (Kho, Sản phẩm, Tồn kho, Lịch sử) | `gpt-6.2-sol` (`medium`) | `src/pages/DashboardPage.tsx`, `src/pages/WarehousesPage.tsx`, `src/pages/ProductsPage.tsx`, `src/pages/InventoryPage.tsx` + unit tests cho từng page (kiểm tra gọi đúng API, cảnh báo `low_stock`, modal Import/Export/Adjust, bộ lọc lịch sử ra/vào kho, ẩn/hiện nút theo Role) | `npm test` PASS 100%, `npm run build` PASS. |
| **FE Part 3**<br/>Toàn bộ màn hình UC01 (Đơn hàng, Thanh toán/Hoàn tiền, Vận đơn) & Docker Config | `gpt-6.2-sol` (`medium`) | `src/pages/OrdersPage.tsx`, `src/pages/ShipmentsPage.tsx`, `src/App.tsx`, `frontend/Dockerfile`, `frontend/nginx.conf`, cập nhật `manager-product-and-warehouse/docker-compose.yml` (thêm mạng `dc-post-net` + service `frontend` cổng `3000:80`, truyền `VITE_DEV_JWT_SECRET` từ `JWT_SECRET`) + unit tests (`OrdersPage.test.tsx`, `ShipmentsPage.test.tsx`) | `npm test` PASS 100%, `npm run build` PASS, `docker compose config` hợp lệ. |
| **FE Part 4**<br/>Build Docker Image, Up Full Stack & E2E Smoke Test | `gpt-6.1-sol` (`low`) | Chạy `docker compose up -d --build` trong `manager-product-and-warehouse/`, xác minh `http://localhost:3000` (HTML SPA), `http://localhost:3000/health` (`{"status":"ok"}`), và gọi thử luồng API thật qua proxy `http://localhost:3000/api/v1/...` (tạo kho/sản phẩm/nhập kho/tạo đơn) để chắc chắn `local -> frontend -> api -> postgres` thông suốt 100%. | Tất cả container `Up`/`healthy`, `/health` & `/api/v1/*` qua `localhost:3000` trả 200/201. |

---

## 4. Checklist Review Bắt Buộc (Tôi sẽ kiểm tra sau khi Codex hoàn thành)
- [ ] Giao diện tiếng Việt chuẩn nghiệp vụ bưu cục/kho vận (ShipBob/GHN/Viettel Post style), responsive, không vỡ layout.
- [ ] Phủ đủ 25 API trong `04_API_Specification.md` (không có nút chết hoặc dữ liệu mock cứng).
- [ ] Hiển thị lỗi từ backend (`error.code`, `error.message`) rõ ràng bằng Toast (VD: `ERR_INSUFFICIENT_STOCK`, `ERR_BELOW_RESERVED_STOCK`, `ERR_WAREHOUSE_ACCESS_DENIED`).
- [ ] Unit test Frontend (`vitest run`) và Backend (`go test ./...`) đều PASS 100%.
- [ ] Docker Desktop chạy đủ 4 service trong cùng mạng `dc-post-net`; gọi từ máy local tới `http://localhost:3000` và proxy `/api/v1/*` xuống `api:8082` hoạt động trơn tru.
