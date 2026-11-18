package vn.edu.gym.model;

public record QuotaView(
        int total,
        int used,
        int reserved,
        int available
) {
    public static QuotaView compute(int total, int used, int reserved) {
        return new QuotaView(total, used, reserved, total - used - reserved);
    }
}
