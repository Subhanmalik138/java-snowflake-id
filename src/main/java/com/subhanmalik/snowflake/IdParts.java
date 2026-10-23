package com.subhanmalik.snowflake;

/**
 * The decoded components of a Snowflake ID.
 *
 * @param timestampMs wall-clock milliseconds (epoch offset already re-added)
 * @param workerId    the worker that produced the ID (0-1023)
 * @param sequence    the per-millisecond sequence number (0-4095)
 */
public record IdParts(long timestampMs, int workerId, int sequence) {}
