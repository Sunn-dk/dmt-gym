package vn.edu.gym.model;

import java.time.LocalDate;

public record ReportSummary(
        LocalDate fromDate,
        LocalDate toDate,
        long totalRevenueVnd,
        long totalVisits,
        long completedPtSessions
) {
}
