package com.subhanmalik.snowflake;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

    private static final long EPOCH = 1_700_000_000_000L;

    @Test
    void idsAreMonotonicallyIncreasing() {
        ManualClock clock = new ManualClock(EPOCH + 1_000);
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(3, EPOCH, clock);
        long prev = gen.nextId();
        for (int i = 0; i < 1_000; i++) {
            clock.advance(i % 3); // sometimes stay in the same millisecond
            long next = gen.nextId();
            assertTrue(next > prev, "IDs must increase: " + prev + " then " + next);
            prev = next;
        }
    }

    @Test
    void idsAreUniqueAcrossConcurrentThreads() throws Exception {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(7);
        int threads = 8;
        int perThread = 2_500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Set<Long>>> futures =
                LongStream.range(0, threads)
                        .mapToObj(
                                t ->
                                        pool.submit(
                                                () -> {
                                                    start.await();
                                                    Set<Long> ids = new HashSet<>();
                                                    for (int i = 0; i < perThread; i++) {
                                                        ids.add(gen.nextId());
                                                    }
                                                    return ids;
                                                }))
                        .toList();
        start.countDown();
        Set<Long> all = Collections.synchronizedSet(new HashSet<>());
        for (Future<Set<Long>> f : futures) {
            for (long id : f.get()) {
                assertTrue(all.add(id), "Duplicate ID detected: " + id);
            }
        }
        assertEquals(threads * perThread, all.size());
        pool.shutdown();
    }

    @Test
    void sequenceIncrementsWithinSameMillisecond() {
        ManualClock clock = new ManualClock(EPOCH + 500);
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1, EPOCH, clock);
        IdParts first = gen.parse(gen.nextId());
        IdParts second = gen.parse(gen.nextId());
        IdParts third = gen.parse(gen.nextId());
        assertEquals(0, first.sequence());
        assertEquals(1, second.sequence());
        assertEquals(2, third.sequence());
        assertEquals(first.timestampMs(), second.timestampMs());
        assertEquals(first.timestampMs(), third.timestampMs());
    }

    @Test
    void sequenceResetsOnNewMillisecond() {
        ManualClock clock = new ManualClock(EPOCH + 500);
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1, EPOCH, clock);
        gen.nextId();
        gen.nextId();
        clock.advance(1);
        IdParts parts = gen.parse(gen.nextId());
        assertEquals(0, parts.sequence());
    }

    @Test
    void sequenceRolloverWaitsForNextMillisecond() throws Exception {
        ManualClock clock = new ManualClock(EPOCH + 500);
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1, EPOCH, clock);
        // A helper thread nudges the clock forward once the generator is spinning.
        Thread nudger =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                            clock.advance(1);
                        });
        nudger.setDaemon(true);
        nudger.start();
        long prev = 0;
        // 4096 IDs fill sequences 0..4095; the 4097th must roll over into the next millisecond.
        for (int i = 0; i <= SnowflakeIdGenerator.MAX_SEQUENCE + 1; i++) {
            long id = gen.nextId();
            assertTrue(id > prev);
            prev = id;
        }
        IdParts rolledOver = gen.parse(prev);
        assertEquals(0, rolledOver.sequence());
        assertEquals(EPOCH + 501, rolledOver.timestampMs());
        nudger.join(2_000);
    }

    @Test
    void parseRoundTripsAllFields() {
        ManualClock clock = new ManualClock(EPOCH + 123_456);
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(512, EPOCH, clock);
        long id = gen.nextId();
        IdParts parts = gen.parse(id);
        assertEquals(EPOCH + 123_456, parts.timestampMs());
        assertEquals(512, parts.workerId());
        assertEquals(0, parts.sequence());
    }

    @Test
    void workerIdIsEmbeddedInId() {
        ManualClock clock = new ManualClock(EPOCH + 1_000);
        for (int worker : new int[] {0, 1, 500, 1023}) {
            SnowflakeIdGenerator gen = new SnowflakeIdGenerator(worker, EPOCH, clock);
            IdParts parts = gen.parse(gen.nextId());
            assertEquals(worker, parts.workerId());
        }
    }

    @Test
    void workerIdOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(SnowflakeIdGenerator.MAX_WORKER_ID + 1));
        assertDoesNotThrow(
                () -> {
                    new SnowflakeIdGenerator(0);
                    new SnowflakeIdGenerator(SnowflakeIdGenerator.MAX_WORKER_ID);
                });
    }

    @Test
    void nullClockIsRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1, EPOCH, null));
    }

    @Test
    void clockMovingBackwardsThrows() {
        ManualClock clock = new ManualClock(EPOCH + 1_000);
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1, EPOCH, clock);
        gen.nextId();
        clock.set(EPOCH + 999);
        assertThrows(IllegalStateException.class, gen::nextId);
    }

    @Test
    void idsStayPositive() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(42);
        for (int i = 0; i < 100; i++) {
            assertTrue(gen.nextId() > 0, "Sign bit must stay 0");
        }
    }
}
