package vn.edu.gym.model;

import java.time.LocalDate;

public record ExpiringMemberView(
        long memberId,
        String fullName,
        String phone,
        LocalDate coverageEnd,
        LocalDate lastUsableDate,
        long daysRemaining
) {
    public String memberCode() {
        return "HV-" + memberId;
    }
}
