package vn.edu.gym.model;

import java.time.LocalDate;

public class SaleCommand {
    private Long memberId;
    private Long planId;
    private SaleType saleType;
    private String requestKey;
    private PlanKind quotedKind;
    private Integer quotedSessionsTotal;
    private Long quotedPriceVnd;
    private Integer quotedDurationDays;
    private LocalDate expectedStart;
    private LocalDate expectedEnd;
    private Long amountReceivedVnd;
    private boolean cashConfirmed;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public SaleType getSaleType() {
        return saleType;
    }

    public void setSaleType(SaleType saleType) {
        this.saleType = saleType;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public PlanKind getQuotedKind() {
        return quotedKind;
    }

    public void setQuotedKind(PlanKind quotedKind) {
        this.quotedKind = quotedKind;
    }

    public Integer getQuotedSessionsTotal() {
        return quotedSessionsTotal;
    }

    public void setQuotedSessionsTotal(Integer quotedSessionsTotal) {
        this.quotedSessionsTotal = quotedSessionsTotal;
    }

    public Long getQuotedPriceVnd() {
        return quotedPriceVnd;
    }

    public void setQuotedPriceVnd(Long quotedPriceVnd) {
        this.quotedPriceVnd = quotedPriceVnd;
    }

    public Integer getQuotedDurationDays() {
        return quotedDurationDays;
    }

    public void setQuotedDurationDays(Integer quotedDurationDays) {
        this.quotedDurationDays = quotedDurationDays;
    }

    public LocalDate getExpectedStart() {
        return expectedStart;
    }

    public void setExpectedStart(LocalDate expectedStart) {
        this.expectedStart = expectedStart;
    }

    public LocalDate getExpectedEnd() {
        return expectedEnd;
    }

    public void setExpectedEnd(LocalDate expectedEnd) {
        this.expectedEnd = expectedEnd;
    }

    public Long getAmountReceivedVnd() {
        return amountReceivedVnd;
    }

    public void setAmountReceivedVnd(Long amountReceivedVnd) {
        this.amountReceivedVnd = amountReceivedVnd;
    }

    public boolean isCashConfirmed() {
        return cashConfirmed;
    }

    public void setCashConfirmed(boolean cashConfirmed) {
        this.cashConfirmed = cashConfirmed;
    }
}
