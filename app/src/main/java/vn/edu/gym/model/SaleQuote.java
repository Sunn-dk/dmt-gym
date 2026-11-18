package vn.edu.gym.model;

import java.time.LocalDate;

public record SaleQuote(
        long memberId,
        String memberName,
        long planId,
        String planName,
        SaleType saleType,
        PlanKind quotedKind,
        int quotedDurationDays,
        Integer quotedSessionsTotal,
        long quotedPriceVnd,
        LocalDate expectedStart,
        LocalDate expectedEnd,
        LocalDate lastUsableDate,
        String requestKey
) {
}
