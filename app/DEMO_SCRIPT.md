# Kịch bản Demo Sản Phẩm DMT Gym (12–15 phút · Prototype + Java + MySQL thật)

## 1. Chuẩn bị trước giờ Demo
1. Đảm bảo dịch vụ `MySQL80` đang chạy và database `gym_exam_demo` đã khởi tạo sạch từ `seed.sql` (nếu muốn demo ngày mới, tạo DB mới theo [`README_RUN.md`](README_RUN.md), không chỉnh giờ hệ thống).
2. Phần **UI/Prototype (mô phỏng):** mở [`../prototype/index.html`](../prototype/index.html) trên trình duyệt để giới thiệu nhanh điều hướng 11 màn hình, phân quyền hiển thị nút, và Light/Dark đồng bộ với app thật.
3. Phần **Java + MySQL thật:** chạy ứng dụng tại `http://localhost:8080` theo [`README_RUN.md`](README_RUN.md):
   ```powershell
   cd app
   $env:GYM_DB_USER = "root"
   $env:GYM_DB_PASSWORD = "<mat_khau_mysql>"
   $env:GYM_DEMO_PASSWORD = "Gym@12345"
   java "-Duser.timezone=Asia/Ho_Chi_Minh" -jar target/gym-management-1.0.0.jar
   ```

---

## 2. Kịch bản 5 Chuỗi Demo End-to-End trên Java + MySQL thật

### Chuỗi 1 — Cấu hình Quản lý & Phân quyền (`/login`, `/users`, `/plans`, `/trainers`)
1. Mở `http://localhost:8080/login`, đăng nhập `manager` / `Gym@12345` (minh họa chuyển nhanh chế độ **Light / Dark** ở góc trên).
2. Vào **Tài khoản** (`/users`): cho thấy 4 tài khoản cố định (`manager`, `reception`, `ptminh`, `ptnam`), giải thích BCrypt cost 12, giới hạn mật khẩu 8–72 byte UTF-8 và khóa Quản lý cuối/tự khóa.
3. Vào **Gói tập** (`/plans`): đối chiếu gói `GYM` theo ngày (`Gym 30 ngày`, `Gym 90 ngày`) và gói `PT` theo buổi (`PT 4 buổi`, `PT 12 buổi`).
4. Vào **PT & Giờ làm** (`/trainers`): xem 2 PT mẫu và khung giờ rảnh theo ngày `05:00–22:00`.

### Chuỗi 2 — Tiếp nhận Hội viên, Mua gói & Phiếu thu (`/members`, `/members/1/sales/new`, `/receipts/{id}`)
1. Đăng xuất và đăng nhập `reception` / `Gym@12345` (menu tự động ẩn **Báo cáo tổng hợp** và **Tài khoản**).
2. Vào **Hội viên** (`/members`): tìm `HV-1 · Hội viên mẫu An` (chỉ có PT chưa có GYM) và tạo mới 1 hội viên đủ 18 tuổi.
3. Chọn **Mua gói mới** cho `HV-1`: chọn `Gym 30 ngày (300.000 đ)`, nhập `300000`, tick xác nhận thu đủ tiền mặt → chuyển sang **Phiếu thu** (`/receipts/...`), giải thích transaction nguyên tử `memberships + payments`, `request_key` chống thu đúp và snapshot bất biến.

### Chuỗi 3 — Đặt lịch PT 60 phút, Chống trùng & Quota (`/members/2`, `/pt-bookings`)
1. Mở chi tiết `HV-2 · Hội viên mẫu Bình` (`/members/2`): chỉ rõ hợp đồng PT `#7` (`Tổng 4 · Đã dùng 1 · Đang giữ 2 · Còn đặt 1`).
2. Vào **Lịch & Kết quả PT** (`/pt-bookings`): đặt thêm 1 buổi 60 phút hợp lệ cho `HV-2` vào ngày mai trong khung rảnh PT → dòng tóm tắt quota cập nhật `Còn đặt 0`.
3. Thử đặt thêm 1 buổi nữa → hệ thống chặn rõ lý do hết quyền đặt mới.
4. Thử hủy 1 lịch tương lai của `HV-2` → giải phóng 1 quyền giữ (`Còn đặt` tăng lại `1`).

### Chuỗi 4 — Ra/Vào phòng tập & Chốt kết quả có xác nhận (`/visits`, `/pt-bookings`)
1. Vào **Ra / vào gym** (`/visits`):
   - Thấy lượt tồn đọng ngày trước của `HV-5 · Hội viên mẫu Hà` → bấm `Xác nhận ra`.
   - Thử check-in `HV-2` (hoặc `HV-1` vừa mua GYM) → rồi bấm `Xác nhận ra`.
2. Đăng xuất, đăng nhập `ptnam` / `Gym@12345` → tự động vào `/pt-bookings` (chỉ thấy lịch của PT Nam).
3. Ở mục **Buổi PT quá giờ kết thúc nhưng chưa xử lý**, bấm **Hoàn thành** trên `BK-3` → hộp thoại xác nhận tóm tắt hiện ảnh hưởng quota → bấm **Xác nhận chốt kết quả** (`Đã dùng +1`, `Đang giữ -1`).

### Chuỗi 5 — Gia hạn GYM, Báo cáo & Thiết bị (`/reports`, `/equipment`)
1. Đăng nhập lại `manager` / `Gym@12345`.
2. Mở Gia hạn GYM cho `HV-2`: ngày bắt đầu mới nối tiếp sau hạn cuối GYM hiện tại (không bị lệch bởi hợp đồng PT).
3. Mở **Báo cáo tổng hợp** (`/reports`): chọn khoảng ngày bao trùm dữ liệu mẫu (trước giao dịch mới là `4.700.000 đ / 3 lượt / 1 buổi`, sau giao dịch Chuỗi 2 & 4 tăng tương ứng — ví dụ `5.000.000 đ / 4 lượt / 2 buổi`).
4. Mở **Thiết bị** (`/equipment`): xem `TM01` (`Sẵn sàng`), `BK01` (`Đang hỏng`) và luồng báo hỏng / bảo trì / khôi phục.

---

## 3. Phụ lục: Dữ liệu mở rộng tùy chọn (`database/demo_extended.sql`)
Nếu chạy thêm `database/demo_extended.sql` trên DB demo sạch sau `seed.sql`, bảng đối soát trước–sau như sau:

| ID mới | Hội viên / Bản ghi | Tình huống minh họa | Tổng trước / sau |
|---|---|---|---|
| `HV-7` (`HĐ #9`, `PT #9`) | Phạm Gần Hết Hạn | GYM `[D-2, D+3)` → còn 2 ngày, **hiển thị** trên bảng nhắc 7 ngày | Seed gốc: `4.700.000 đ` |
| `HV-8` (`HĐ #10–#11`) | Lê Gia Hạn Xa | GYM `[D-2, D+3)` + gia hạn `[D+3, D+33)` → **không bị nhắc** 7 ngày | Cộng thêm: `+2.600.000 đ` |
| `HV-9` (`HĐ #12–#14`, `Visit #4`, `BK #5–#9`) | Trần Hết Quyền PT | GYM tương lai `SCHEDULED`, PT `#14` `4/1/3/0` (hết quyền), `BK-6` quá giờ thiếu visit (`NO_ATTENDANCE`), `BK-9` `CANCELLED` | Tổng sau mở rộng: **`7.300.000 đ`** (`15` phiếu thu, `4` lượt vào gốc + `1` mới) |
| `HV-10` (`HĐ #15`) | Đỗ Hết Hạn Hôm Nay | GYM `[D-29, D+1)` → `Hết hạn cuối hôm nay` (0 ngày) trên bảng nhắc | `0` dòng dị thường trên `verify.sql` |
