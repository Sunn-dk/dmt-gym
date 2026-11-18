package vn.edu.gym.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared request parameter parsing and form state preservation helpers so invalid query strings,
 * decimal numbers in integer fields, or business validation errors never fall back to Whitelabel
 * error pages and always preserve non-secret user input.
 */
public final class ControllerParamUtils {

    private static final Map<String, String> DEFAULT_FIELD_LABELS = Map.ofEntries(
            Map.entry("name", "Tên gói / thiết bị"),
            Map.entry("kind", "Loại gói"),
            Map.entry("durationDays", "Thời hạn (ngày)"),
            Map.entry("sessionsTotal", "Số buổi PT"),
            Map.entry("priceVnd", "Giá tiền (VNĐ)"),
            Map.entry("fullName", "Họ và tên"),
            Map.entry("phone", "Số điện thoại"),
            Map.entry("birthDate", "Ngày sinh"),
            Map.entry("specialty", "Chuyên môn"),
            Map.entry("date", "Ngày"),
            Map.entry("bookingDate", "Ngày tập"),
            Map.entry("fromDate", "Từ ngày"),
            Map.entry("toDate", "Đến ngày"),
            Map.entry("startTime", "Giờ bắt đầu"),
            Map.entry("endTime", "Giờ kết thúc"),
            Map.entry("code", "Mã thiết bị"),
            Map.entry("location", "Vị trí"),
            Map.entry("note", "Ghi chú"),
            Map.entry("amountReceivedVnd", "Số tiền mặt đã nhận"),
            Map.entry("page", "Số trang"),
            Map.entry("trainerId", "Huấn luyện viên"),
            Map.entry("memberId", "Hội viên"),
            Map.entry("membershipId", "Hợp đồng PT"),
            Map.entry("targetStatus", "Trạng thái đích"),
            Map.entry("active", "Trạng thái hoạt động"),
            Map.entry("username", "Tên đăng nhập"),
            Map.entry("role", "Vai trò")
    );

    private ControllerParamUtils() {
    }

    public static String labelFor(String field) {
        return DEFAULT_FIELD_LABELS.getOrDefault(field, "Trường dữ liệu");
    }

    public static Map<String, String> extractSafeInputs(HttpServletRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        if (request == null) {
            return values;
        }
        request.getParameterMap().forEach((key, arr) -> {
            if (key == null || "_csrf".equals(key)) {
                return;
            }
            String lower = key.toLowerCase();
            // Never reflect raw passwords back into rendered HTML attributes or models
            if (lower.contains("password") || lower.contains("pwd")) {
                return;
            }
            values.put(key, (arr != null && arr.length > 0) ? arr[0] : "");
        });
        return values;
    }

    public static String describeBindingErrors(BindingResult result) {
        List<String> messages = new ArrayList<>();
        for (FieldError err : result.getFieldErrors()) {
            String label = labelFor(err.getField());
            String code = err.getCode() == null ? "" : err.getCode();
            if (code.contains("typeMismatch")) {
                messages.add(label + " không đúng định dạng hoặc kiểu giá trị hợp lệ.");
            } else {
                messages.add(label + " không hợp lệ.");
            }
        }
        if (messages.isEmpty()) {
            return "Dữ liệu gửi lên không đúng định dạng hợp lệ.";
        }
        return String.join(" ", messages);
    }

    public static LocalDate parseOptionalDate(String raw, String fieldLabel, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            errors.add(fieldLabel + " không đúng định dạng ngày (YYYY-MM-DD).");
            return null;
        }
    }

    public static Long parseOptionalLong(String raw, String fieldLabel, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            errors.add(fieldLabel + " phải là mã số hợp lệ.");
            return null;
        }
    }

    public static int parsePage(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            errors.add("Số trang không hợp lệ hoặc vượt quá giới hạn cho phép.");
            return 0;
        }
    }
}
