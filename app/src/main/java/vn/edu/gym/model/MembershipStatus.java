package vn.edu.gym.model;

public enum MembershipStatus {
    SCHEDULED("Chưa bắt đầu"),
    ACTIVE("Đang hiệu lực"),
    EXPIRED("Hết hạn");

    private final String label;

    MembershipStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
