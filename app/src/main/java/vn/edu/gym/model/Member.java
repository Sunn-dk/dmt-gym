package vn.edu.gym.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Member(
        long id,
        String fullName,
        String phone,
        LocalDate birthDate,
        boolean active,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName
) {
    public String memberCode() {
        return "HV-" + id;
    }
}
