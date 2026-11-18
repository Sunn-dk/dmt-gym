package vn.edu.gym.model;

public record Plan(
        long id,
        String name,
        PlanKind kind,
        int durationDays,
        Integer sessionsTotal,
        long priceVnd,
        boolean active
) {
}
