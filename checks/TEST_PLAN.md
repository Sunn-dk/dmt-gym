# Kế hoạch kiểm thử bản 4 — DMT Gym

42 kịch bản nghiệm thu chính (kết quả thực tế theo từng lần chạy được ghi trong `checks/test_log.csv`, phân biệt rõ số JUnit test methods và 42 acceptance scenarios) + bộ regression bổ sung `QA-R01`–`QA-R11`. `TC-42` kiểm `FR-25` Should. `D` là ngày `Clock` test; dùng MySQL thật cho khóa/transaction, không thay bằng mock/H2.

| Test | FR/NFR | Dữ liệu/bước | Mong đợi |
| --- | --- | --- | --- |
| TC-01 | FR-01, NFR-02 | Sai/đúng mật khẩu; user inactive; logout; hết phiên 30 phút | Không cấp phiên sai; vai trò đúng; mọi trang nghiệp vụ chặn sau logout/hết phiên. |
| TC-02 | FR-02, NFR-02 | Lễ tân gửi POST sửa gói/active HV; người chưa login gọi URL; thiếu CSRF | 403/đăng nhập theo trường hợp; DB không thay đổi. |
| TC-03 | FR-03 | Tạo HV hợp lệ; tên rỗng; SĐT sai/trùng; đúng sinh nhật 18 và thiếu 1 ngày | Chỉ hợp lệ và đủ 18 được lưu; lỗi rõ từng trường. |
| TC-04 | FR-04 | Sửa liên hệ; ngừng HV có gói/visit nhưng không có BOOKED; thử HV còn BOOKED | Lịch sử còn nguyên; có BOOKED thì chặn ngừng; ngừng thành công vẫn cho check-out. |
| TC-05 | FR-05 | Tìm theo ID/tên/SĐT, không có kết quả, &gt;20 kết quả | Đúng bộ lọc; rỗng rõ; phân trang không mất/lặp bản ghi. |
| TC-06 | FR-06 | Hai vai trò xem gói; gói inactive | Cả hai xem; chỉ gói active xuất hiện trong form bán. |
| TC-07 | FR-07 | Ngày 0/3651/1.5; giá 0/âm/100000001; tên trống; giá hợp lệ; ngừng bán | Từ chối sai; chỉ quản lý sửa; lịch sử không đổi. |
| TC-08 | FR-08 | D=2026-11-17; mua GYM30 và thử mua GYM thêm khi còn hạn; mua PT riêng | GYM [D,D+30), không chồng; PT mới riêng không ảnh hưởng GYM. |
| TC-09 | FR-09, NFR-03 | Thu 299999/300001; thiếu xác nhận; thu đúng 300000 | Chỉ lần đúng tạo 1 membership và 1 payment cùng số tiền. |
| TC-10 | FR-09, NFR-03 | Giả lập lỗi DB sau INSERT membership trước INSERT payment | Rollback: số membership và payment đều không tăng. |
| TC-11 | FR-09, NFR-03 | Gửi cùng key hai lần/đồng thời; timeout sau commit; key cũ đổi plan; đổi giá trước submit | Cùng phiếu một lần; khác payload REQUEST_CONFLICT; quote lỗi PRICE_CHANGED không ghi. |
| TC-12 | FR-10 | Gia hạn GYM khi còn10ngày, có hợp đồng PT dài hơn; sau đó gia hạn tiếp; GYM hết hạn | Chỉ max end GYM; [D+10,D+40), [D+40,D+70); hết hạn bắt đầuD. |
| TC-13 | FR-10, NFR-03 | Hai key gia hạn cùng HV, cùng quote khoảng cũ từ hai kết nối | Một commit; còn lại COVERAGE_CHANGED; sau xác nhận khoảng mới thì nối tiếp, không chồng. |
| TC-14 | FR-11 | Kiểm ngày start−1/start/end−1/end; ngày 28/02 năm nhuận và 31/12 | Lần lượt SCHEDULED/ACTIVE/ACTIVE/EXPIRED; cộng ngày qua tháng/năm đúng; active HV độc lập. |
| TC-15 | FR-12, NFR-03 | Mở phiếu, sửa tên/giá/thời hạn plan, mở lại phiếu; gửi POST sửa phiếu | Tên gói/giá/ngày snapshot cũ không đổi; không có chức năng sửa phiếu. |
| TC-16 | FR-13 | Check-in hợp lệ, inactive, hết hạn, chỉ có gói tương lai; giờ 04:59/05:00/21:59/22:00 | Chỉ hợp lệ trong giờ được nhận; đúng boundary; từ chối không tạo visit. |
| TC-17 | FR-13, NFR-03 | Hai check-in đồng thời; check-in khi có lượt mở cũ; ra rồi vào lại cùng ngày | Tối đa một lượt mở; lượt cũ chặn; sau check-out được tạo lượt mới. |
| TC-18 | FR-14 | Check-out rồi lặp; gói hết hạn/HV inactive; sau 22h; clock trước giờ vào | Lưu một lần, vẫn cho ra; chỉ CLOCK_ERROR bị chặn; timestamp cũ giữ nguyên. |
| TC-19 | FR-15 | Lọc visits theo hội viên và khoảng ngày vào (tối đa 366 ngày; thiếu 1 đầu lọc từ chối; bỏ trống 2 đầu dùng mặc định 31 ngày), có visit mở | Đúng [from 00:00,to+1 00:00), cả mở/đóng; 367 ngày hoặc thiếu 1 đầu báo lỗi không chạy query lịch sử; sort check_in_at DESC,id DESC; 20 dòng/trang. |
| TC-20 | NFR-01 | 1.000 HV/10.000 visits; làm nóng 5; đo 20 lần tìm và danh sách | Ít nhất 19/20 mỗi loại ≤2s; đính kèm cấu hình và thời gian thô. |
| TC-21 | NFR-02 | Kiểm DB hash BCrypt; log; tham số SQL và chuỗi HTML nhập vào tên | Không mật khẩu rõ; SQL có bind; HTML escape; không in secret; CSRF và phiên 30 phút có hiệu lực. |
| TC-22 | NFR-03 | Chèn FK sai, số tiền âm, hai payment một membership, hai open visit, visit sai chủ gói | DB từ chối; query đối soát số kỳ thiếu phiếu =0 và amount≠snapshot=0. |
| TC-23 | NFR-04 | Mua gói/ra vào, restart app+DB; backup rồi restore DB mới | Đếm bản ghi/tổng tiền và quyền gói giữ nguyên; không tự seed lại. |
| TC-24 | NFR-05 | 1366×768, bàn phím, lỗi form, trạng thái rỗng | Đọc và thao tác được; không mất input hợp lệ; thông báo không chỉ màu. |
| TC-25 | NFR-06 | Build và chạy source theo README trên máy thứ hai, tắt Internet sau setup | 5 nhóm nghiệp vụ chạy; ghi JDK/Spring/MySQL cụ thể và lệnh thật. |
| TC-26 | NFR-07 | 10.000 HV/100.000 visits, 2 phiên; 20 truy vấn mỗi loại sau làm nóng | ≥19/20 ≤3s; không trùng open visit/kỳ; nếu không đo được ghi NOT_RUN, không PASS. |
| TC-27 | FR-16, FR-02, NFR-02 | Tạo 3 role; trùng user/trainer; password ngắn/over72bytes; khóa/reset; 2 quản lý khóa nhau | Hash thật; role/trainer đúng; phiên cũ bị chặn request kế; không tự khóa hoặc mất quản lý cuối. |
| TC-28 | FR-17 | Thêm/sửa/ngừng PT; còn BOOKED quá giờ và tương lai | Dữ liệu hợp lệ lưu; có BOOKED chặn ngừng, lịch sử giữ nguyên. |
| TC-29 | FR-18 | Khung 08–10; thêm 09–11, 10–12; sửa/xóa khung có BOOKED; PT sửa khung người khác | Trùng bị chặn; liền kề cho phép; không phá lịch đã đặt; kiểm owner. |
| TC-30 | FR-07, FR-08, FR-09 | GYM có sessions; PT NULL/0 sessions; mua PT khi còn GYM; sửa danh mục PT sau bán | Type constraint đúng; hợp đồng riêng, snapshot loại/số buổi giữ nguyên, một phiếu đủ tiền. |
| TC-31 | FR-19, FR-21 | Đặt PT 60 phút tương lai đúng khung/GYM/PT; lọc lịch theo ngày/HV/PT | Một BOOKED, available giảm1, đúng bộ lọc; repeated identical booking không tạo thêm. |
| TC-32 | FR-19 | PT đã có 08–09, đặt 08:30–09:30; HV đặt PT khác cùng giờ; đặt 09–10 | Hai xung đột bị chặn kể cả khác gói; liền kề nhận nếu đủ điều kiện. |
| TC-33 | FR-11, FR-19 | Gói4, used1,reserved2→available1; đặt thêm rồi đặt nữa; hợp đồng PT khác | Chỉ một quyền còn lại; không gộp quota giữa hợp đồng; không kiểm mỗi used. |
| TC-34 | FR-19 | Thiếu GYM; sai chủ PT; gói/PT/HV inactive; đúng start/end ngày; ngoài khung; quá khứ | Chặn đúng từng nguyên nhân; gói PT không cấp quyền GYM; end_date loại trừ. |
| TC-35 | FR-19, NFR-03 | Hai connection đặt cùng PT/giờ hoặc cùng HV/quyền cuối ở 2 PT khác | Chỉ một commit; không chồng lịch hoặc âm quota; giữ thống nhất thứ tự khóa. |
| TC-36 | FR-20 | Hủy BOOKED trước giờ, hủy lại, hủy đúng start; đặt lại sau hủy | Giải phóng đúng1; lần lặp không đổi; now&gt;=start chặn; giữ bản ghi CANCELLED. |
| TC-37 | FR-22 | Hoàn thành sau end có visit giao thời gian, lặp; chưa end; visit ngoài khoảng; chuyển kết quả cuối | Used tăng1 đúng lần đầu; còn lại từ chối hoặc trả kết quả cũ, không trừ hai lần. |
| TC-38 | FR-23 | NO_SHOW trước/sau end, lặp; đổi từ COMPLETED; BOOKED quá giờ | Chỉ sauend từBOOKED; không tiêu buổi; BOOKED chưa chốt vẫn reserved; trạng thái cuối không đảo. |
| TC-39 | FR-24 | Lọc seed/all ngày; tiền/lượt/buổi; GYM sắp hết hạn và đã gia hạn; khoảng rỗng/sai | Tiền 4.700.000đ, visit3, completed1 với seed; không cộng membership; sắp hết hạn maxend; sai DB không giả0. |
| TC-40 | FR-02, FR-21, FR-22, FR-24 | PT gọi URL lịch người khác/phiếu/báo cáo; lễ tân hoàn thành; quản lý xử lý lịch PT bị khóa | Đúng scope và role; không lộ tiền; Quản lý vẫn xử lý kết quả, owner kiểm backend. |
| TC-41 | FR-04, FR-17 | Ngừng HV/PT còn BOOKED; xử lý các lịch rồi ngừng | Chặn và liệt kê; sau hết BOOKED được ngừng; gói/lịch cũ không bị xóa. |
| TC-42 | FR-25 | Thiết bị mã trùng; báo hỏng; nhân viên tự đánh đã sửa; quản lý chuyển bảo trì/khôi phục | Unique và quyền đúng; có ghi chú/actor/time; state hợp lệ; không liên đới PT booking. |

Mỗi kết quả phải có actual, log/ảnh, người chạy, thời gian. PASS cấu trúc hoặc prototype không chuyển thành PASS Java. Mục tiêu 24/24 FR Must có minh chứng; không đếm test hay UC thay FR đã hoàn thành.
