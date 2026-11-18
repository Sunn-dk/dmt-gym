package vn.edu.gym.model;

import java.time.LocalDateTime;

public record AvailabilitySlot(
        long id,
        long trainerId,
        String trainerName,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
