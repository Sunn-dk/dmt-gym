package vn.edu.gym.exception;

import org.springframework.http.HttpStatus;

/**
 * Canonical business and system error codes.
 */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Dữ liệu nhập không hợp lệ."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi yêu cầu."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "Số điện thoại đã tồn tại ở hồ sơ hội viên khác."),
    MEMBER_INACTIVE(HttpStatus.CONFLICT, "Hội viên đang ở trạng thái ngừng phục vụ."),
    PLAN_INACTIVE(HttpStatus.CONFLICT, "Gói tập hiện đã ngừng bán."),
    ALREADY_COVERED(HttpStatus.CONFLICT, "Hội viên đã có gói GYM đang hiệu lực hoặc sắp hiệu lực; vui lòng chọn Gia hạn GYM."),
    NO_MEMBERSHIP(HttpStatus.CONFLICT, "Hội viên chưa từng mua gói GYM trước đây; vui lòng chọn Mua mới."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "Số tiền thu phải khớp chính xác với giá gói và cần xác nhận đã nhận đủ tiền mặt."),
    PRICE_CHANGED(HttpStatus.CONFLICT, "Thông tin giá, loại hoặc thời hạn gói vừa thay đổi; vui lòng xác nhận lại báo giá mới."),
    COVERAGE_CHANGED(HttpStatus.CONFLICT, "Khoảng ngày hiệu lực GYM vừa thay đổi do giao dịch khác; vui lòng xác nhận lại khoảng ngày mới."),
    REQUEST_CONFLICT(HttpStatus.CONFLICT, "Mã yêu cầu (request_key) đã được dùng cho giao dịch khác."),
    NO_ACTIVE_MEMBERSHIP(HttpStatus.CONFLICT, "Hội viên không có hợp đồng GYM đang hiệu lực tại ngày hôm nay (gói PT không cấp quyền vào cửa)."),
    ALREADY_INSIDE(HttpStatus.CONFLICT, "Hội viên hiện đang có lượt vào phòng tập chưa check-out."),
    OUTSIDE_OPENING_HOURS(HttpStatus.CONFLICT, "Ngoài giờ mở cửa phòng gym (05:00 - 22:00)."),
    CLOCK_ERROR(HttpStatus.CONFLICT, "Thời điểm xử lý không hợp lệ so với mốc thời gian trước đó."),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi lưu trữ dữ liệu; giao dịch đã được hoàn tác. Vui lòng thử lại."),
    PENDING_BOOKINGS(HttpStatus.CONFLICT, "Vẫn còn lịch PT ở trạng thái Đã đặt (BOOKED) chưa được xử lý."),
    AVAILABILITY_CONFLICT(HttpStatus.CONFLICT, "Khung giờ rảnh bị trùng với khung giờ đã khai báo của huấn luyện viên."),
    INELIGIBLE_BOOKING(HttpStatus.CONFLICT, "Không đủ điều kiện đặt buổi PT (kiểm tra trạng thái hội viên/PT, hạn gói PT và gói GYM tại ngày tập)."),
    OUTSIDE_AVAILABILITY(HttpStatus.CONFLICT, "Thời gian buổi PT phải nằm trong tương lai, cùng một ngày (05:00 - 22:00) và trọn trong khung giờ rảnh của PT."),
    TRAINER_CONFLICT(HttpStatus.CONFLICT, "Huấn luyện viên đã có lịch PT trùng trong khoảng thời gian này."),
    MEMBER_CONFLICT(HttpStatus.CONFLICT, "Hội viên đã có lịch PT trùng trong khoảng thời gian này."),
    NO_PT_CREDIT(HttpStatus.CONFLICT, "Hợp đồng PT này đã hết số buổi có thể đặt (available = 0)."),
    CANCELLATION_TOO_LATE(HttpStatus.CONFLICT, "Chỉ được hủy lịch PT trước giờ bắt đầu buổi tập."),
    SESSION_NOT_ENDED(HttpStatus.CONFLICT, "Buổi PT chưa kết thúc nên chưa thể chốt Hoàn thành hoặc Vắng mặt."),
    NO_ATTENDANCE(HttpStatus.CONFLICT, "Không tìm thấy lượt check-in GYM hợp lệ của hội viên giao với thời gian buổi tập."),
    INVALID_STATE(HttpStatus.CONFLICT, "Trạng thái hiện tại không cho phép thực hiện chuyển đổi này."),
    LAST_MANAGER_OR_SELF(HttpStatus.CONFLICT, "Không thể tự khóa tài khoản đang đăng nhập hoặc khóa Quản lý đang hoạt động cuối cùng.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
