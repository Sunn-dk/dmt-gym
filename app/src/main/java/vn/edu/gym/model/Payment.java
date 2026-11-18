package vn.edu.gym.model;

import java.time.LocalDateTime;

public record Payment(
        long id,
        long membershipId,
        long amountVnd,
        String method,
        LocalDateTime paidAt,
        long collectedBy
) {
}
