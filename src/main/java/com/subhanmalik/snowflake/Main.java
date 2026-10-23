package com.subhanmalik.snowflake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny CLI around {@link SnowflakeIdGenerator}.
 *
 * <pre>
 *   java -jar snowflake-id.jar generate [-n count] [-w workerId]
 *   java -jar snowflake-id.jar parse &lt;id&gt; [&lt;id&gt; ...]
 * </pre>
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            usage(1);
        }
        switch (args[0]) {
            case "generate" -> generate(args);
            case "parse" -> parse(args);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                usage(1);
            }
        }
    }

    private static void generate(String[] args) {
        int count = 10;
        int worker = 1;
        List<String> rest = new ArrayList<>(List.of(args).subList(1, args.length));
        for (int i = 0; i < rest.size(); i++) {
            switch (rest.get(i)) {
                case "-n" -> count = Integer.parseInt(nextArg(rest, ++i, "-n"));
                case "-w" -> worker = Integer.parseInt(nextArg(rest, ++i, "-w"));
                default -> die("Unknown option: " + rest.get(i));
            }
        }
        if (count < 1) {
            die("count must be >= 1");
        }
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(worker);
        for (int i = 0; i < count; i++) {
            System.out.println(gen.nextId());
        }
    }

    private static void parse(String[] args) {
        if (args.length < 2) {
            die("parse needs at least one id");
        }
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0);
        for (int i = 1; i < args.length; i++) {
            long id;
            try {
                id = Long.parseUnsignedLong(args[i]);
            } catch (NumberFormatException e) {
                die("Not a valid 64-bit id: " + args[i]);
                return;
            }
            IdParts parts = gen.parse(id);
            System.out.printf(
                    "id=%d  time=%s  worker=%d  sequence=%d%n",
                    id, Instant.ofEpochMilli(parts.timestampMs()), parts.workerId(), parts.sequence());
        }
    }

    private static String nextArg(List<String> args, int index, String flag) {
        if (index >= args.size()) {
            die("Missing value for " + flag);
        }
        return args.get(index);
    }

    private static void die(String message) {
        System.err.println("Error: " + message);
        usage(1);
    }

    private static void usage(int exitCode) {
        System.err.println("Usage:");
        System.err.println("  java -jar snowflake-id.jar generate [-n count] [-w workerId]");
        System.err.println("  java -jar snowflake-id.jar parse <id> [<id> ...]");
        System.exit(exitCode);
    }
}
