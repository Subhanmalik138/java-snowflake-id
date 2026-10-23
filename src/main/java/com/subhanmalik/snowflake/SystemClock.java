package com.subhanmalik.snowflake;

/** {@link Clock} backed by the system wall clock. */
public final class SystemClock implements Clock {
    @Override
    public long millis() {
        return System.currentTimeMillis();
    }
}
