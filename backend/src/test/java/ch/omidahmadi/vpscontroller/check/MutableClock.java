package ch.omidahmadi.vpscontroller.check;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock whose time the test sets explicitly. */
class MutableClock extends java.time.Clock {

    private Instant instant;
    private final ZoneId zone;

    MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public java.time.Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    void set(Instant newInstant) {
        this.instant = newInstant;
    }

    void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }
}
