# Snowflake ID Generator (Java)

A thread-safe, Twitter-style Snowflake ID generator in pure Java 21 — no dependencies.
Produces unique, roughly time-ordered 64-bit IDs that work as sortable database
primary keys without a separate timestamp index.

## The idea

A UUID is unique but random; a serial is ordered but not unique across machines.
A Snowflake ID packs both into 64 bits:

```
 1 bit   sign (always 0)
41 bits  milliseconds since a custom epoch (2025-01-01) — ~69 years of room
10 bits  worker id (0–1023) — up to 1024 independent generators
12 bits  per-millisecond sequence (0–4095) — 4M IDs/sec per worker
```

IDs from the same generator are strictly increasing, so `ORDER BY id` is
`ORDER BY created_at` for free.

## Design decisions

1. **Injectable clock, not `System.currentTimeMillis()` scattered in the code.**
   The generator takes a `Clock` interface. Production uses `SystemClock`;
   tests use `ManualClock` to pin and rewind time. This made the clock-skew
   and sequence-rollover behavior provable rather than hoped-for.
2. **Clock regression throws instead of waiting.** If the wall clock moves
   backwards (NTP correction), issuing more IDs could duplicate earlier ones.
   Waiting silently would stall callers; throwing forces the operator to notice
   and fix the clock. Uniqueness is the one guarantee this library sells.
3. **Sequence exhaustion spins to the next millisecond.** At >4096 IDs/ms the
   generator busy-waits for the next tick instead of blocking on a lock —
   simpler and faster than a semaphore, and backpressure belongs to the caller.
4. **A `record` for decoded parts.** `IdParts` is a value type by construction:
   no builders, no setters, pattern-matchable.

## How to run

Requires JDK 17 or newer (tested on JDK 21).

```bash
./build.sh            # compiles to out/ and builds out/snowflake-id.jar
java -jar out/snowflake-id.jar generate -n 10 -w 7
java -jar out/snowflake-id.jar parse 239326500166332416
```

Or compile by hand:

```bash
javac -d out/main $(find src/main/java -name '*.java')
java -cp out/main com.subhanmalik.snowflake.Main generate -n 5
```

## How to run tests

Tests use JUnit 5 (jar fetched from Maven Central by `build.sh` if missing):

```bash
./build.sh test
```

11 tests cover: monotonic ordering, 20k IDs unique across 8 threads, sequence
increment/reset/rollover within a millisecond, clock-backwards rejection,
worker-id bit layout, parse round-trip, and boundary validation.

## Tech stack

- Java 21, zero runtime dependencies
- JUnit 5 for tests
