package vn.edu.gym.model;

import java.time.LocalDateTime;

public record VisitView(
        long id,
        long memberId,
        String memberName,
        String memberPhone,
        long membershipId,
        String planNameSnapshot,
        LocalDateTime checkInAt,
        LocalDateTime checkOutAt,
        long checkInBy,
        String checkInByName,
        Long checkOutBy,
        String checkOutByName,
        boolean inside,
        boolean staleFromPreviousDay
) {
    public String memberCode() {
        return "HV-" + memberId;
    }
}
