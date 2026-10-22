package com.captainsss.usershub;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Drain stdout concurrently so a silent/hung process cannot bypass the deadline. */
public final class RootCommand {
    public static final class Result {
        public final boolean ok; public final String output;
        Result(boolean ok, String output) { this.ok = ok; this.output = output; }
    }
    public static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    public static Result run(String command) { return run(command, null); }
    public static Result run(String command, byte[] input) {
        ExecutorService io = Executors.newFixedThreadPool(2);
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            final Process child = process;
            Future<String> reader = io.submit(() -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
                try (InputStream in = child.getInputStream()) {
                    while ((n = in.read(buf)) != -1) {
                        int remaining = 1048576 - out.size(); if (remaining > 0) out.write(buf, 0, Math.min(n, remaining));
                    }
                }
                return out.toString("UTF-8");
            });
            Future<?> writer = io.submit(() -> {
                try (OutputStream out = child.getOutputStream()) { if (input != null) out.write(input); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            });
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly(); return new Result(false, "操作超过 180 秒，已停止等待。请刷新确认系统实际状态。");
            }
            writer.get(2, TimeUnit.SECONDS);
            return new Result(process.exitValue() == 0, reader.get(2, TimeUnit.SECONDS).trim());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Result(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) process.destroy(); io.shutdownNow();
        }
    }
}
