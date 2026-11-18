package vn.edu.gym;

import vn.edu.gym.config.ClockConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controllable Clock for deterministic time-boundary tests (BR-01 / TEST_PLAN.md).
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Instant> instantRef;

    public MutableClock(LocalDateTime initial) {
        this.zone = ClockConfig.GYM_ZONE;
        this.instantRef = new AtomicReference<>(initial.atZone(zone).toInstant());
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.instantRef.set(dateTime.atZone(zone).toInstant());
    }

    public void setDate(LocalDate date) {
        setDateTime(date.atTime(10, 0));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instantRef.get(), zone);
    }

    @Override
    public Instant instant() {
        return instantRef.get();
    }
}
