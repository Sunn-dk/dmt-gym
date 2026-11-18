package vn.edu.gym.model;

public enum EquipmentStatus {
    AVAILABLE("Sẵn sàng"),
    BROKEN("Đang hỏng"),
    MAINTENANCE("Đang bảo trì");

    private final String label;

    EquipmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
