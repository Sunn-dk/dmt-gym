# DMT Gym — Prototype giao diện 11 màn hình (Mô phỏng)

- Mở [`index.html`](index.html) để duyệt mô phỏng 11 màn hình (`UI-01` → `UI-11`) theo giao diện **Berry Studio Light & Dark** đồng bộ với ứng dụng Java/MySQL **DMT Gym**, có nút chuyển đổi **Light / Dark** lưu qua `localStorage` (`gym-theme`).
- Đây là **mockup mô phỏng trong bộ nhớ** (ngày nền minh họa `17/11/2026`), chỉ dùng để minh họa điều hướng, trường dữ liệu, ánh xạ route Java (`FR-01`–`FR-25`) và phân quyền hiển thị nút.
- Mọi xử lý thật (xác thực `BCrypt`, phân quyền backend + sở hữu PT, transaction tiền tệ, chống gửi lặp, khóa tương tranh MySQL 8) nằm trong mã nguồn Java tại [`../app/`](../app/README_RUN.md). Ảnh trong `screenshots/` là ảnh wireframe minh họa riêng, tách biệt với ảnh chụp ứng dụng Java thật trong `../app/evidence/`.
