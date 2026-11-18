# CSDL nghiệp vụ bản 4

MySQL 8.4 InnoDB/utf8mb4, 9 bảng Must và equipment Should. **DDL/seed chưa được thực thi trên MySQL trong lượt sửa thiết kế.** Agent nền tảng phải import và kiểm trước khi bàn giao. Không dùng H2/SQLite để tuyên bố MySQL đạt.

## Khởi tạo demo, không phá dữ liệu

1. Tạo DB mới `gym_exam_demo`, credential qua env; import schema.sql một lần, không auto-reset khi startup.
2. Chạy seed_trainers.sql tạo 2 PT.
3. Bootstrap 4 users với hash BCrypt cost12 từ biến GYM_DEMO_PASSWORD (không commit password/hash giả). id1 manager/MANAGER/trainer NULL; id2 reception/RECEPTIONIST/NULL; id3 ptminh/TRAINER/trainer1; id4 ptnam/TRAINER/trainer2. credential_version=0, active=true; full_name là tên demo tương ứng. Chỉ bootstrap trên bảng users rỗng, không ghi đè dữ liệu có sẵn.
4. Chạy seed.sql bằng client dừng khi gặp lỗi; rollback nếu có lỗi. Seed đặt UTC+7 và D=ngày chạy. Không chạy lại trên DB đã có dữ liệu.
5. Chạy verify.sql, kiểm anomaly query 0 dòng và tổng/đếm mẫu. Seed có 4 users, 2 PT, 6 HV, 5 plans, 8 memberships/8 payments, 3 visits (1 mở), 5 khung rảnh, 4 booking, 2 thiết bị; tổng thu 4.700.000đ.

An có PT nhưng chưa có GYM để minh họa thiếu quyền vào; Bình có GYM còn10ngày và PT4buổi: used1,reserved2,available1. Bình có BOOKED tương lai và BOOKED hôm qua chưa chốt, có visit đúng giờ để demo hoàn thành. Chi hết GYM; Dũng inactive; Hà còn visit mở ngày trước; Lan có kỳ gia hạn GYM bắt đầu hôm nay. Seed giữ lịch sử đầy đủ, không dùng trạng thái thiếu phiếu.

## Quan hệ và cách lưu

- users→trainers là 0/1–0/1; chỉ TRAINER có trainer_id, unique. Role không sửa sau tạo. credential_version tăng khi khóa/reset, phiên nhớ version và bị chặn nếu lệch. Không lưu plaintext password.
- members→memberships 1–n; plans→memberships 1–n. kind/sessions/duration/price/name đều snapshot; không đọc danh mục hiện tại để cấp quyền cũ.
- memberships→payments 1–0/1 ở DB (unique); service đảm bảo đúng1 sau bán commit. amount bằng price snapshot là invariant service và verify, không có trigger.
- visits và pt_bookings dùng FK kép (member_id,membership_id) trỏ chủ hợp đồng. Service còn phải kiểm visits dùng GYM và bookings dùng PT; FK không tự kiểm loại.
- trainers→availability/bookings 1–n. Quota tính từ trạng thái booking; không cột used/remaining. UNIQUE(trainer,start) không chặn mọi overlap, nên không dùng thay khóa + kiểm khoảng.
- open_member_id là generated column của visits; UNIQUE cho tối đa1 visit mở/HV. NULL cho lượt đã đóng. Không INSERT giá trị vào cột generated.
- equipment độc lập lịch PT, chỉ danh mục/trạng thái hiện tại; không maintenance_job hay lịch sử sửa từng lần.

## Từ điển cột (sinh từ DDL)

Danh sách dưới đây giữ đúng tên/kiểu cột; PK/FK/CHECK/index đầy đủ trong schema.sql. Không đổi tên trong Java rồi bỏ quên ERD.

### trainers

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `full_name` | `VARCHAR(100) NOT NULL` |
| `specialty` | `VARCHAR(150) NOT NULL` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |

### users

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `username` | `VARCHAR(50) NOT NULL UNIQUE` |
| `password_hash` | `VARCHAR(100) NOT NULL` |
| `full_name` | `VARCHAR(100) NOT NULL` |
| `role` | `VARCHAR(20) NOT NULL` |
| `trainer_id` | `BIGINT NULL UNIQUE` |
| `credential_version` | `INT NOT NULL DEFAULT 0` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |

### members

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `full_name` | `VARCHAR(100) NOT NULL` |
| `phone` | `VARCHAR(10) NOT NULL UNIQUE` |
| `birth_date` | `DATE NOT NULL` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `DATETIME(0) NOT NULL` |
| `created_by` | `BIGINT NOT NULL` |

### plans

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `name` | `VARCHAR(100) NOT NULL` |
| `kind` | `VARCHAR(3) NOT NULL` |
| `duration_days` | `INT NOT NULL` |
| `sessions_total` | `INT NULL` |
| `price_vnd` | `BIGINT NOT NULL` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |

### memberships

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `member_id` | `BIGINT NOT NULL` |
| `plan_id` | `BIGINT NOT NULL` |
| `sale_type` | `VARCHAR(10) NOT NULL` |
| `request_key` | `CHAR(36) NOT NULL UNIQUE` |
| `plan_name_snapshot` | `VARCHAR(100) NOT NULL` |
| `kind_snapshot` | `VARCHAR(3) NOT NULL` |
| `duration_days_snapshot` | `INT NOT NULL` |
| `sessions_total_snapshot` | `INT NULL` |
| `price_vnd_snapshot` | `BIGINT NOT NULL` |
| `start_date` | `DATE NOT NULL` |
| `end_date` | `DATE NOT NULL` |
| `sold_at` | `DATETIME(0) NOT NULL` |
| `sold_by` | `BIGINT NOT NULL` |

### trainer_availability

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `trainer_id` | `BIGINT NOT NULL` |
| `start_at` | `DATETIME(0) NOT NULL` |
| `end_at` | `DATETIME(0) NOT NULL` |

### pt_bookings

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `member_id` | `BIGINT NOT NULL` |
| `membership_id` | `BIGINT NOT NULL` |
| `trainer_id` | `BIGINT NOT NULL` |
| `start_at` | `DATETIME(0) NOT NULL` |
| `end_at` | `DATETIME(0) NOT NULL` |
| `status` | `VARCHAR(12) NOT NULL DEFAULT 'BOOKED'` |
| `created_at` | `DATETIME(0) NOT NULL` |
| `created_by` | `BIGINT NOT NULL` |
| `resolved_at` | `DATETIME(0) NULL` |
| `resolved_by` | `BIGINT NULL` |

### equipment

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `code` | `VARCHAR(30) NOT NULL UNIQUE` |
| `name` | `VARCHAR(100) NOT NULL` |
| `location` | `VARCHAR(100) NOT NULL` |
| `status` | `VARCHAR(12) NOT NULL DEFAULT 'AVAILABLE'` |
| `note` | `VARCHAR(255) NOT NULL DEFAULT ''` |
| `updated_at` | `DATETIME(0) NOT NULL` |
| `updated_by` | `BIGINT NOT NULL` |

### payments

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `membership_id` | `BIGINT NOT NULL UNIQUE` |
| `amount_vnd` | `BIGINT NOT NULL` |
| `method` | `VARCHAR(4) NOT NULL DEFAULT 'CASH'` |
| `paid_at` | `DATETIME(0) NOT NULL` |
| `collected_by` | `BIGINT NOT NULL` |

### visits

| Cột | Kiểu / ràng buộc trực tiếp |
|---|---|
| `id` | `BIGINT PRIMARY KEY AUTO_INCREMENT` |
| `member_id` | `BIGINT NOT NULL` |
| `membership_id` | `BIGINT NOT NULL` |
| `check_in_at` | `DATETIME(0) NOT NULL` |
| `check_out_at` | `DATETIME(0) NULL` |
| `check_in_by` | `BIGINT NOT NULL` |
| `check_out_by` | `BIGINT NULL` |
| `open_member_id` | `BIGINT GENERATED ALWAYS AS` |

## Ràng buộc service và khóa

Tiền VND dùng long nguyên; ngày LocalDate; giờ LocalDateTime với Clock Asia/Ho_Chi_Minh. Không float, không nhận actor/role từ form.

Thứ tự khóa PT: trainer→member→membership→booking; nếu cần plan thì sau member/trước membership. Khóa trainer khi thêm/sửa khung rảnh. Bán/ra-vào chỉ khóa member và các bảng liên quan, không ngược lại lấy trainer. READ_COMMITTED, đọc lịch/quota sau khi khóa. Ngừng HV/PT phải kiểm không còn BOOKED dưới khóa cha tương ứng. Tất cả đường ghi tuân thứ tự, không tự commit ở repository.

Tạo/khóa/reset tài khoản: giữ kiểm quyền và bảo vệ quản lý cuối trong transaction; khóa các user MANAGER liên quan theo ID tăng dần. Cấm tự khóa tài khoản hiện tại. Credential_version có tác dụng khi lớp kiểm phiên đọc user active/version mỗi request, không chỉ khi login.

Request_key của sale giữ nguyên khi retry; so member/plan/type/giá/loại/số buổi/thời hạn/start/end/amount với snapshot. Key đúng đã commit trả phiếu cũ trước kiểm active/giá biến động; key khác payload báo REQUEST_CONFLICT. Unique conflict phải rollback rồi đọc lại ở transaction mới. Không thu thêm tiền vì timeout chưa biết kết quả.

Đặt PT trùng hoàn toàn HV/PT/hợp đồng/start/end đang BOOKED thì trả bản ghi cũ; trường hợp overlap khác báo lỗi. Không cần bảng idempotency riêng cho booking. Chốt/hủy dùng UPDATE ... WHERE status='BOOKED', trạng thái cuối cùng loại trả kết quả cũ, khác loại bị từ chối.

Các kiểm tra tuổi, phone, ngày, quyền, type hợp đồng, quota, overlap, giờ rảnh, visit giao buổi và báo cáo thực hiện ở service. DB CHECK/FK/UNIQUE không thay những điều kiện này. Không xóa vật lý dữ liệu tham chiếu, không CASCADE DELETE.

## Truy vấn tổng hợp chuẩn

Tiền: SUM(payments.amount_vnd) theo paid_at. Lượt: COUNT(visits) theo check_in_at. PT: COUNT(*) status=COMPLETED theo end_at. Khoảng [from 00:00,to+1 00:00), tối đa366ngày; chỉ MANAGER.

Sắp hết hạn GYM: nhóm theo member, chỉ kind_snapshot=GYM; cần EXISTS kỳ start<=today<end và member.active; coverage_end=MAX(end_date) của mọi kỳ GYM đã mua; last_usable=coverage_end−1 trong [today,today+7]. Không lấy kỳ đang ACTIVE riêng vì đã có gia hạn tương lai.

Danh sách phân trang20, sort ổn định bằng thời gian+ID. LIKE phải bind và xử lý ký tự wildcard theo lựa chọn UI. Các trường nhạy cảm không trả cho PT; PT chỉ nhận tên/ID HV và dấu vết buổi mình cần xử lý.
