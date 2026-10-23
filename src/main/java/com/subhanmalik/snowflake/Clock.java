package com.subhanmalik.snowflake;

/**
 * Abstraction over wall-clock time.
 *
 * <p>The generator depends on this instead of {@link System#currentTimeMillis()}
 * directly so tests can pin, advance or rewind time deterministically with
 * {@link ManualClock}.
 */
@FunctionalInterface
public interface Clock {
    long millis();
}
