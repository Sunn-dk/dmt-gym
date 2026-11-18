package vn.edu.gym.model;

public enum SaleType {
    PURCHASE("Mua mới"),
    RENEWAL("Gia hạn GYM");

    private final String label;

    SaleType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
