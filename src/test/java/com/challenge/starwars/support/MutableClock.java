package com.challenge.starwars.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Un reloj que los tests pueden adelantar para ejercitar la expiración sin dormir. */
public final class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    public void reset() {
        now = Instant.now();
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
