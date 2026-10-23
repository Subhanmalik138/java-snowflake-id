package com.subhanmalik.snowflake;

/**
 * Thread-safe generator of Snowflake-style 64-bit IDs.
 *
 * <p>Layout (most significant to least):
 * <pre>
 *  1 bit   sign (always 0)
 * 41 bits  milliseconds since {@link #DEFAULT_EPOCH_MS}
 * 10 bits  worker id (0-1023)
 * 12 bits  per-millisecond sequence (0-4095)
 * </pre>
 *
 * <p>IDs are monotonically increasing within one generator instance and are
 * roughly time-ordered, so they can be used as primary keys that sort by
 * creation time without a separate index.
 *
 * <p>Clock regression (time moving backwards, e.g. NTP corrections) is handled
 * by throwing {@link IllegalStateException}: silently waiting would stall
 * callers, and issuing duplicate IDs would corrupt uniqueness guarantees.
 */
public final class SnowflakeIdGenerator {

    /** Default epoch: 2025-01-01T00:00:00Z — keeps the 41-bit window usable for ~69 years. */
    public static final long DEFAULT_EPOCH_MS = 1_735_689_600_000L;

    public static final int MAX_WORKER_ID = 1023;
    public static final int MAX_SEQUENCE = 4095;

    private static final long WORKER_ID_SHIFT = 12L;
    private static final long TIMESTAMP_SHIFT = 22L;

    private final long epochMs;
    private final int workerId;
    private final Clock clock;

    private long lastTimestamp = -1L;
    private int sequence = 0;

    /** Creates a generator with the default epoch and the system clock. */
    public SnowflakeIdGenerator(int workerId) {
        this(workerId, DEFAULT_EPOCH_MS, new SystemClock());
    }

    /** Creates a generator with a custom epoch and clock (useful for tests). */
    public SnowflakeIdGenerator(int workerId, long epochMs, Clock clock) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be in [0, " + MAX_WORKER_ID + "], got " + workerId);
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.workerId = workerId;
        this.epochMs = epochMs;
        this.clock = clock;
    }

    /**
     * Returns the next unique ID. Thread-safe.
     *
     * @throws IllegalStateException if the clock moved backwards
     */
    public synchronized long nextId() {
        long timestamp = clock.millis();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards by " + (lastTimestamp - timestamp)
                            + "ms; refusing to generate IDs to preserve uniqueness");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted for this millisecond: spin until the next one.
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;
        return ((timestamp - epochMs) << TIMESTAMP_SHIFT)
                | ((long) workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /** Decodes an ID back into its timestamp, worker id and sequence parts. */
    public IdParts parse(long id) {
        int sequence = (int) (id & MAX_SEQUENCE);
        int workerId = (int) ((id >>> WORKER_ID_SHIFT) & MAX_WORKER_ID);
        long timestamp = (id >>> TIMESTAMP_SHIFT) + epochMs;
        return new IdParts(timestamp, workerId, sequence);
    }

    public int workerId() {
        return workerId;
    }

    public long epochMs() {
        return epochMs;
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = clock.millis();
        while (timestamp <= lastTimestamp) {
            timestamp = clock.millis();
        }
        return timestamp;
    }
}
