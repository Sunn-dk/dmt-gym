package vn.edu.gym.model;

public enum Role {
    MANAGER("Quản lý"),
    RECEPTIONIST("Lễ tân"),
    TRAINER("Huấn luyện viên");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
