# DMT Gym (`v1`) — Hệ thống Quản lý Phòng Gym

Ứng dụng quản lý phòng tập **DMT Gym** (hội viên, gói GYM theo ngày & gói PT theo buổi, thu tiền mặt & phiếu thu bất biến, check-in/out 05:00–22:00, lịch PT 60 phút, báo cáo tổng hợp và quản lý thiết bị), xây dựng bằng **Java 21+ / Spring Boot 3.4 / Thymeleaf / Spring Security / Spring JDBC / MySQL 8**, hỗ trợ **Light/Dark Mode** và giao diện **Desktop · Tablet · Mobile**.

> Tài liệu cài đặt & kịch bản test chi tiết được xuất bản kèm theo tại [`HUONG_DAN_CAI_DAT_VA_TEST_DMT_GYM.pdf`](HUONG_DAN_CAI_DAT_VA_TEST_DMT_GYM.pdf). File JAR dựng sẵn được đính kèm tại mục **GitHub Releases (`v1`)**.

---

## 1. Cấu trúc Repository

```text
dmt-gym/
├── app/                                   # Mã nguồn Spring Boot + Thymeleaf + Maven Wrapper + JUnit tests
│   ├── src/
│   ├── evidence/                          # Ảnh chụp giao diện Desktop, Tablet, Mobile (Light & Dark)
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd                    # Maven Wrapper tích hợp sẵn (không cần cài Maven rời)
│   ├── .env.example                       # Mẫu biến môi trường (không chứa mật khẩu thật)
│   ├── README_RUN.md                      # Hướng dẫn build & vận hành chi tiết
│   └── DEMO_SCRIPT.md                     # Kịch bản demo 5 chuỗi nghiệp vụ (12–15 phút)
├── database/                              # DDL & dữ liệu mẫu MySQL 8 (InnoDB, utf8mb4)
│   ├── schema.sql
│   ├── seed_trainers.sql
│   ├── seed.sql
│   ├── demo_extended.sql                  # Tình huống demo mở rộng tùy chọn
│   └── verify.sql                         # Truy vấn đối soát dị thường & tổng tiền
├── prototype/                             # Bản mẫu giao diện tĩnh HTML/CSS/JS (mở trực tiếp index.html)
├── checks/                                # TEST_PLAN.md (42 ca) & test_log.csv
├── start-dmt-gym.ps1                      # Script PowerShell khởi chạy nhanh 1 lệnh
├── HUONG_DAN_CAI_DAT_VA_TEST_DMT_GYM.pdf  # Tài liệu hướng dẫn cài đặt & test đầy đủ (PDF)
└── HUONG_DAN_NHANH.md                     # Hướng dẫn tóm tắt
```

---

## 2. Yêu cầu Môi trường

- **Java JDK 21 trở lên** (biên dịch `--release 21`)
- **MySQL Server 8.0 / 8.4** (`InnoDB`, `utf8mb4`, múi giờ `Asia/Ho_Chi_Minh`)
- Trình duyệt hiện đại (Chrome, Edge, Firefox)

---

## 3. Cài đặt & Chạy nhanh (Windows PowerShell)

1. **Tạo 2 database rỗng UTF-8 trong MySQL:**
   ```sql
   CREATE DATABASE IF NOT EXISTS gym_exam_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE DATABASE IF NOT EXISTS gym_exam_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```

2. **Khởi chạy ứng dụng** (từ thư mục gốc của repo):
   ```powershell
   .\start-dmt-gym.ps1
   ```
   - Nếu đã tải `gym-management-1.0.0.jar` từ **Release `v1`** đặt vào `app/` (hoặc build sẵn vào `app/target/`), script chạy trực tiếp file JAR; nếu chưa có JAR, script tự động chạy qua `.\app\mvnw.cmd spring-boot:run`.
   - Khi `gym_exam_demo` đang trống, `DatabaseBootstrap` tự khởi tạo 10 bảng, 2 PT mẫu, 4 tài khoản `BCrypt(12)` và dữ liệu mẫu ban đầu (không ghi đè khi khởi động lại).

3. **Hoặc build/chạy thủ công bằng Maven Wrapper:**
   ```powershell
   cd app
   $env:GYM_DB_USER = "root"
   $env:GYM_DB_PASSWORD = "<mat_khau_mysql>"
   $env:GYM_DEMO_PASSWORD = "Gym@12345"
   .\mvnw.cmd spring-boot:run
   ```

4. **Truy cập ứng dụng tại `http://localhost:8080`**

---

## 4. Tài khoản Demo Mặc định

Mật khẩu khởi tạo mặc định qua `GYM_DEMO_PASSWORD`: `Gym@12345`

| Tài khoản | Vai trò | Phạm vi |
|---|---|---|
| `manager` | Quản lý (`MANAGER`) | Toàn quyền quản trị, tài khoản, gói tập, PT, hội viên, báo cáo, thiết bị |
| `reception` | Lễ tân (`RECEPTIONIST`) | Tiếp nhận hội viên, bán/gia hạn gói, phiếu thu, check-in/out, đặt/hủy lịch PT, báo hỏng thiết bị |
| `ptminh` | Huấn luyện viên (`TRAINER`) | Khung giờ rảnh cá nhân, xem và chốt `Hoàn thành` / `Vắng mặt` buổi PT của mình |
| `ptnam` | Huấn luyện viên (`TRAINER`) | Khung giờ rảnh và lịch PT phụ trách riêng của PT Nam |

---

## 5. Kiểm thử Tự động (JUnit 5)

```powershell
cd app

# 10 test Web / Security / Thymeleaf / Validation (không cần MySQL)
.\mvnw.cmd -B test -Dtest=WebSecurityAndTemplateTest

# Toàn bộ 21 test (gồm 11 integration & tranh chấp đa kết nối trên gym_exam_test)
$env:GYM_DB_USER = "root"
$env:GYM_DB_PASSWORD = "<mat_khau_mysql>"
$env:GYM_DEMO_PASSWORD = "Gym@12345"
.\mvnw.cmd -B verify
```
