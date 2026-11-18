package vn.edu.gym.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReceiptView(
        long paymentId,
        long membershipId,
        long memberId,
        String memberName,
        String memberPhone,
        String planNameSnapshot,
        PlanKind kindSnapshot,
        int durationDaysSnapshot,
        Integer sessionsTotalSnapshot,
        long priceVndSnapshot,
        SaleType saleType,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate lastUsableDate,
        long amountVnd,
        String method,
        LocalDateTime paidAt,
        long collectedBy,
        String collectedByName
) {
    public String receiptCode() {
        return "PT-" + paymentId;
    }

    public String memberCode() {
        return "HV-" + memberId;
    }
}
