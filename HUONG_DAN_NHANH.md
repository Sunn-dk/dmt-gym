# Gói Demo & Kiểm thử Hệ thống Quản lý Phòng Gym — DMT Gym (Bản 4)

> Tài liệu chi tiết kèm lệnh đầy đủ và checklist kiểm thử được xuất bản PDF tại [`HUONG_DAN_CAI_DAT_VA_TEST_DMT_GYM.pdf`](HUONG_DAN_CAI_DAT_VA_TEST_DMT_GYM.pdf).

## 1. Cấu trúc thư mục gói gửi test
- [`HUONG_DAN_CAI_DAT_VA_TEST_DMT_GYM.pdf`](HUONG_DAN_CAI_DAT_VA_TEST_DMT_GYM.pdf): Hướng dẫn cài đặt từ đầu và kịch bản test đầy đủ (PDF).
- [`start-dmt-gym.ps1`](start-dmt-gym.ps1): Script PowerShell khởi chạy nhanh máy chủ DMT Gym trên cổng `8080`.
- `app/`: Mã nguồn Spring Boot + Thymeleaf + Maven Wrapper (`mvnw.cmd`/`mvnw`), kèm `README_RUN.md`, `DEMO_SCRIPT.md` và ảnh đối chiếu `app/evidence/` (file JAR dựng sẵn tải tại GitHub Release `v1`).
- `database/`: `schema.sql`, `seed_trainers.sql`, `seed.sql`, `demo_extended.sql` (tùy chọn), `verify.sql`.
- `prototype/`: Mockup tĩnh HTML/CSS/JS mở trực tiếp [`prototype/index.html`](prototype/index.html) trên trình duyệt.
- `checks/`: [`TEST_PLAN.md`](checks/TEST_PLAN.md) (`TC-01`..`TC-42`) và [`test_log.csv`](checks/test_log.csv).

## 2. Chạy nhanh trên máy người test (Windows PowerShell)
1. Cài **JDK 21+** và **MySQL 8.0 / 8.4** đang chạy trên cổng `3306`.
2. Tạo 2 database rỗng UTF-8:
   ```sql
   CREATE DATABASE IF NOT EXISTS gym_exam_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE DATABASE IF NOT EXISTS gym_exam_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```
3. Mở PowerShell tại thư mục này (`DMT_Gym_Demo_Package`) và chạy:
   ```powershell
   .\start-dmt-gym.ps1
   ```
   *(Lần đầu DB trống, ứng dụng tự chạy 5 bước khởi tạo bảng, 2 PT mẫu, 4 tài khoản BCrypt-12 và dữ liệu mẫu; các lần sau giữ nguyên dữ liệu.)*
4. Mở trình duyệt truy cập **`http://localhost:8080`** và đăng nhập thử với mật khẩu `Gym@12345`:
   - Quản lý: `manager`
   - Lễ tân: `reception`
   - Huấn luyện viên: `ptminh`, `ptnam`
