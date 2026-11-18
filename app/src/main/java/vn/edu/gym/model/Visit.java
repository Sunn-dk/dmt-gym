package vn.edu.gym.model;

import java.time.LocalDateTime;

public record Visit(
        long id,
        long memberId,
        long membershipId,
        LocalDateTime checkInAt,
        LocalDateTime checkOutAt,
        long checkInBy,
        Long checkOutBy
) {
    public boolean isOpen() {
        return checkOutAt == null;
    }
}
