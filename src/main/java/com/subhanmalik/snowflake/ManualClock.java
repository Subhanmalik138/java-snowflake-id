package com.subhanmalik.snowflake;

/**
 * A clock whose time is advanced manually.
 *
 * <p>Intended for tests and demos: set the instant explicitly and step it
 * forward with {@link #advance(long)}. Not thread-safe; use from a single
 * thread.
 */
public final class ManualClock implements Clock {
    private volatile long millis;

    public ManualClock(long startMillis) {
        this.millis = startMillis;
    }

    @Override
    public long millis() {
        return millis;
    }

    /** Moves the clock forward (or backward) by the given milliseconds. */
    public void advance(long deltaMillis) {
        this.millis += deltaMillis;
    }

    /** Sets the clock to an absolute instant. */
    public void set(long millis) {
        this.millis = millis;
    }
}
