package vn.edu.gym.model;

public enum BookingStatus {
    BOOKED("Đã đặt"),
    CANCELLED("Đã hủy"),
    COMPLETED("Hoàn thành"),
    NO_SHOW("Vắng mặt");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
