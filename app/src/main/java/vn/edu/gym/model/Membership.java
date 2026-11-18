package vn.edu.gym.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Membership(
        long id,
        long memberId,
        long planId,
        SaleType saleType,
        String requestKey,
        String planNameSnapshot,
        PlanKind kindSnapshot,
        int durationDaysSnapshot,
        Integer sessionsTotalSnapshot,
        long priceVndSnapshot,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime soldAt,
        long soldBy
) {
    public LocalDate lastUsableDate() {
        return endDate.minusDays(1);
    }

    public MembershipStatus statusAt(LocalDate today) {
        if (today.isBefore(startDate)) {
            return MembershipStatus.SCHEDULED;
        }
        if (today.isBefore(endDate)) {
            return MembershipStatus.ACTIVE;
        }
        return MembershipStatus.EXPIRED;
    }
}
