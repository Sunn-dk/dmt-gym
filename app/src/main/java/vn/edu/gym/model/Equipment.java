package vn.edu.gym.model;

import java.time.LocalDateTime;

public record Equipment(
        long id,
        String code,
        String name,
        String location,
        EquipmentStatus status,
        String note,
        LocalDateTime updatedAt,
        long updatedBy,
        String updatedByName
) {
}
