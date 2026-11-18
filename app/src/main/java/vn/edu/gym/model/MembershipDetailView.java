package vn.edu.gym.model;

import java.time.LocalDate;

public record MembershipDetailView(
        Membership membership,
        MembershipStatus status,
        LocalDate lastUsableDate,
        QuotaView quota,
        Long paymentId,
        String soldByName
) {
}
