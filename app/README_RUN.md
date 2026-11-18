# Hướng dẫn Build, Cấu hình MySQL và Chạy Ứng dụng DMT Gym

## 1. Môi trường đã kiểm chứng & phạm vi tương thích
- **Phiên bản đã chạy & kiểm thử thực tế trên máy dev:** `Java 27` (biên dịch `--release 21`), Spring Boot `3.4.13`, Maven Wrapper `3.9.16`, MySQL `8.0.46` (`MySQL80`, Windows 11, múi giờ `+07:00`).
- **Phạm vi tương thích mục tiêu:** JDK `21+` và MySQL `8.0` / `8.4` (`InnoDB`, `utf8mb4`). *(Lưu ý: chạy độc lập trên JDK 21 / MySQL 8.4 hoặc trên máy thứ hai chưa được thực nghiệm tại máy đơn lẻ này.)*
- **Build tool:** Maven Wrapper tích hợp sẵn (`mvnw.cmd` cho Windows, `mvnw` cho Linux/macOS).

---

## 2. Biến môi trường cấu hình
Không lưu mật khẩu DB thật trong mã nguồn. Tập tin [`.env.example`](.env.example) **chỉ là mẫu tham khảo** — Spring Boot không tự đọc file `.env`; khi chạy cần export biến môi trường trực tiếp trong PowerShell.

| Biến môi trường | Ý nghĩa | Mặc định trong `application.properties` |
|---|---|---|
| `GYM_DB_URL` | JDBC URL tới MySQL `gym_exam_demo` | `jdbc:mysql://localhost:3306/gym_exam_demo?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Ho_Chi_Minh&characterEncoding=utf8` |
| `GYM_DB_USER` | Tài khoản MySQL | `root` |
| `GYM_DB_PASSWORD` | Mật khẩu MySQL | *(trống — truyền trước khi chạy)* |
| `GYM_DEMO_PASSWORD` | Mật khẩu khởi tạo cho 4 tài khoản mẫu khi `users` trống | `Gym@12345` |
| `GYM_PORT` | Cổng HTTP của ứng dụng | `8080` |
| `GYM_TEST_DB_URL` | JDBC URL cho JUnit integration test (`application-test.properties`) | `jdbc:mysql://localhost:3306/gym_exam_test?...` |

---

## 3. Khởi tạo CSDL MySQL (`gym_exam_demo`) lần đầu (PowerShell)
> Lưu ý: PowerShell không hỗ trợ dấu `<` chuyển hướng stdin kiểu bash, và `mysql.exe` có thể chưa nằm sẵn trên `PATH`. Hãy trỏ trực tiếp tới `mysql.exe` và đọc file UTF-8 không BOM.

1. Tạo hai database rỗng (`gym_exam_demo` cho chạy live và `gym_exam_test` cho JUnit):
   ```powershell
   $mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
   $env:MYSQL_PWD = "<mat_khau_mysql>"
   & $mysql -u root -e "CREATE DATABASE IF NOT EXISTS gym_exam_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS gym_exam_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
   ```

2. Đặt biến môi trường và khởi chạy ứng dụng lần đầu để `DatabaseBootstrap` tự nạp 5 bước chuẩn (`schema.sql` → `seed_trainers.sql` → 4 users `BCrypt(12)` → `seed.sql`):
   ```powershell
   cd app
   $env:GYM_DB_USER = "root"
   $env:GYM_DB_PASSWORD = "<mat_khau_mysql>"
   $env:GYM_DEMO_PASSWORD = "Gym@12345"
   .\mvnw.cmd spring-boot:run
   ```
   `DatabaseBootstrap` **không bao giờ** `DROP TABLE` hay xóa dữ liệu ở các lần khởi động lại sau khi DB đã có bảng/dữ liệu.

3. Chạy đối soát `database/verify.sql` trên PowerShell:
   ```powershell
   $mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
   $env:MYSQL_PWD = "<mat_khau_mysql>"
   $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
   $OutputEncoding = $utf8NoBom
   [Console]::OutputEncoding = $utf8NoBom
   $sqlText = [System.IO.File]::ReadAllText("../database/verify.sql", [System.Text.Encoding]::UTF8).TrimStart([char]0xFEFF)
   $sqlText | & $mysql -u root --default-character-set=utf8mb4 -t gym_exam_demo
   ```
   Kết quả chuẩn: 8 truy vấn kiểm tra dị thường đầu trả `0 dòng`, `total_paid_vnd = 4700000`, hợp đồng PT `#7` của Bình có `total=4, used=1, reserved=2, available=1`.

4. *(Tùy chọn cho buổi demo phong phú — chạy trên DB demo mới đã có seed gốc):*
   ```powershell
   $extSql = [System.IO.File]::ReadAllText("../database/demo_extended.sql", [System.Text.Encoding]::UTF8).TrimStart([char]0xFEFF)
   $extSql | & $mysql -u root --default-character-set=utf8mb4 -t gym_exam_demo
   ```

---

## 4. Build, Chạy Test, Offline & Backup/Restore (PowerShell)
Trong thư mục `app`:

- **Chạy kiểm thử Web / Security / Thymeleaf (không cần MySQL):**
  ```powershell
  .\mvnw.cmd -B test -Dtest=WebSecurityAndTemplateTest
  ```
- **Chạy toàn bộ kiểm thử tích hợp & tương tranh trên MySQL thật (`gym_exam_test`):**
  ```powershell
  $env:GYM_DB_USER = "root"
  $env:GYM_DB_PASSWORD = "<mat_khau_mysql>"
  $env:GYM_DEMO_PASSWORD = "Gym@12345"
  .\mvnw.cmd -B verify
  ```
- **Build/Chạy ngoại tuyến (Offline sau khi đã tải dependency Maven ở bước trên):**
  ```powershell
  .\mvnw.cmd -o -B verify
  ```
- **Đóng gói và chạy file JAR độc lập:**
  ```powershell
  .\mvnw.cmd -B package -DskipTests
  java "-Duser.timezone=Asia/Ho_Chi_Minh" -jar target/gym-management-1.0.0.jar
  ```
- **Sao lưu & khôi phục thử sang DB riêng (không chạm `gym_exam_demo`):**
  ```powershell
  $mysqldump = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
  $mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
  $env:MYSQL_PWD = "<mat_khau_mysql>"
  & $mysqldump -u root --default-character-set=utf8mb4 --single-transaction --routines --triggers "--result-file=backup_demo.sql" gym_exam_demo
  & $mysql -u root -e "CREATE DATABASE IF NOT EXISTS gym_exam_restore_check CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
  $OutputEncoding = $utf8NoBom
  [Console]::OutputEncoding = $utf8NoBom
  [System.IO.File]::ReadAllText("backup_demo.sql", [System.Text.Encoding]::UTF8).TrimStart([char]0xFEFF) | & $mysql -u root --default-character-set=utf8mb4 gym_exam_restore_check
  ```

---

## 5. Tài khoản mặc định & URL truy cập
Sau khi chạy tại `http://localhost:8080`:
- Quản lý: `manager` / `$GYM_DEMO_PASSWORD` (mặc định `Gym@12345`) → vào `/members`
- Lễ tân: `reception` / `$GYM_DEMO_PASSWORD` → vào `/members`
- PT Minh: `ptminh` / `$GYM_DEMO_PASSWORD` → vào `/pt-bookings`
- PT Nam: `ptnam` / `$GYM_DEMO_PASSWORD` → vào `/pt-bookings`
