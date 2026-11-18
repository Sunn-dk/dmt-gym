package vn.edu.gym.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class BookingInput {
    private Long memberId;
    private Long membershipId;
    private Long trainerId;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalDateTime startAt;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(Long membershipId) {
        this.membershipId = membershipId;
    }

    public Long getTrainerId() {
        return trainerId;
    }

    public void setTrainerId(Long trainerId) {
        this.trainerId = trainerId;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime resolveStartAt() {
        if (startAt != null) {
            return startAt;
        }
        if (bookingDate != null && startTime != null) {
            return LocalDateTime.of(bookingDate, startTime);
        }
        return null;
    }
}
