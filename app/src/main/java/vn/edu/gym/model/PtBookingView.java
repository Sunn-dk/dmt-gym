package vn.edu.gym.model;

import java.time.LocalDateTime;

public record PtBookingView(
        long id,
        long memberId,
        String memberName,
        long membershipId,
        String planNameSnapshot,
        long trainerId,
        String trainerName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        BookingStatus status,
        boolean overduePending,
        boolean canResolveNow,
        boolean hasMatchingAttendance,
        String attendanceSummary,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName,
        LocalDateTime resolvedAt,
        Long resolvedBy,
        String resolvedByName
) {
    public String bookingCode() {
        return "BK-" + id;
    }

    public String memberCode() {
        return "HV-" + memberId;
    }
}
