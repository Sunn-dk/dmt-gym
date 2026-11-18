package vn.edu.gym.model;

import java.time.LocalDateTime;

public record PtBooking(
        long id,
        long memberId,
        long membershipId,
        long trainerId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        BookingStatus status,
        LocalDateTime createdAt,
        long createdBy,
        LocalDateTime resolvedAt,
        Long resolvedBy
) {
}
