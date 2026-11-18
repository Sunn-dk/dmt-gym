package vn.edu.gym.model;

public enum PlanKind {
    GYM("GYM theo ngày"),
    PT("PT theo buổi");

    private final String label;

    PlanKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
