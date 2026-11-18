package vn.edu.gym.model;

public class PlanForm {
    private String name;
    private PlanKind kind = PlanKind.GYM;
    private Integer durationDays;
    private Integer sessionsTotal;
    private Long priceVnd;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlanKind getKind() {
        return kind;
    }

    public void setKind(PlanKind kind) {
        this.kind = kind;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public Integer getSessionsTotal() {
        return sessionsTotal;
    }

    public void setSessionsTotal(Integer sessionsTotal) {
        this.sessionsTotal = sessionsTotal;
    }

    public Long getPriceVnd() {
        return priceVnd;
    }

    public void setPriceVnd(Long priceVnd) {
        this.priceVnd = priceVnd;
    }
}
