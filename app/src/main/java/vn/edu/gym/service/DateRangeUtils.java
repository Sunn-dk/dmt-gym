package vn.edu.gym.service;

import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Shared date-range boundary validation used by both report queries and visit history filters.
 */
public final class DateRangeUtils {

    public static final int MAX_FILTER_DAYS = 366;

    private DateRangeUtils() {
    }

    public record DateRange(LocalDate fromDate, LocalDate toDate) {
    }

    /**
     * Resolves an inclusive [fromDate, toDate] range:
     * - both null -> uses the supplied finite default window,
     * - exactly one non-null -> rejects with INVALID_INPUT to prevent unbounded queries,
     * - fromDate after toDate or span exceeding maxDays -> rejects with INVALID_INPUT.
     */
    public static DateRange resolveBoundedRange(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate defaultFrom,
            LocalDate defaultTo,
            int maxDays
    ) {
        if (fromDate == null && toDate == null) {
            return new DateRange(defaultFrom, defaultTo);
        }
        if (fromDate == null || toDate == null) {
            throw new GymException(
                    ErrorCode.INVALID_INPUT,
                    "Vui lòng nhập đủ cả Từ ngày và Đến ngày (tối đa " + maxDays + " ngày), hoặc để trống cả hai."
            );
        }
        if (fromDate.isAfter(toDate)) {
            throw new GymException(ErrorCode.INVALID_INPUT, "Từ ngày không được lớn hơn Đến ngày.");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(fromDate, toDate.plusDays(1));
        if (inclusiveDays > maxDays) {
            throw new GymException(
                    ErrorCode.INVALID_INPUT,
                    "Khoảng thời gian lọc tối đa " + maxDays + " ngày (đang chọn " + inclusiveDays + " ngày)."
            );
        }
        return new DateRange(fromDate, toDate);
    }
}
