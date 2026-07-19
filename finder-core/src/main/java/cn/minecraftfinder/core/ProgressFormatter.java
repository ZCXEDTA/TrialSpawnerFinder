package cn.minecraftfinder.core;

import java.util.concurrent.TimeUnit;

public final class ProgressFormatter {
    private ProgressFormatter() {
    }

    public static String phase(
            String phase, long completed, long total, String unit, long elapsedNanos) {
        double elapsedSeconds = Math.max(0.001, elapsedNanos / 1_000_000_000.0);
        double throughput = completed / elapsedSeconds;
        long remainingNanos = completed == 0 ? 0
                : Math.max(0, Math.round((double) elapsedNanos * (total - completed) / completed));
        return "%s | %.1f %s/秒 | ETA %s".formatted(
                bar(phase, completed, total), throughput, unit, duration(remainingNanos));
    }

    public static String estimatedWork(
            String phase, long completed, long total, long processed,
            long estimatedWork, String unit, long elapsedNanos) {
        return estimatedWork(
                phase, completed, total, processed, estimatedWork,
                unit, 0, elapsedNanos);
    }

    public static String estimatedWork(
            String phase, long completed, long total, long processed,
            long estimatedWork, String unit, long initialProcessed, long elapsedNanos) {
        double elapsedSeconds = Math.max(0.001, elapsedNanos / 1_000_000_000.0);
        long throughput = Math.round(Math.max(0, processed - initialProcessed) / elapsedSeconds);
        long remainingNanos = completed >= total || throughput == 0 ? 0
                : Math.max(0, Math.round((estimatedWork - processed)
                        / (double) throughput * 1_000_000_000.0));
        long progressTotal = completed >= total
                ? processed : Math.max(estimatedWork, processed + 1);
        return "%s | %s %s | %s %s/秒 | ETA %s".formatted(
                bar(phase, processed, progressTotal), compact(processed), unit,
                compact(throughput), unit, duration(remainingNanos));
    }

    private static String bar(String phase, long completed, long total) {
        int filled = total == 0 ? 10 : (int) Math.min(10, completed * 10 / total);
        int percent = total == 0 ? 100
                : (int) Math.min(100, Math.round(completed * 100.0 / total));
        String prefix = phase == null || phase.isBlank() ? "" : phase + " ";
        return "[%s%s%s] %d%% %d/%d".formatted(
                prefix, "#".repeat(filled), "-".repeat(10 - filled),
                percent, completed, total);
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) return "%.1fB".formatted(value / 1_000_000_000.0);
        if (value >= 1_000_000L) return "%.1fM".formatted(value / 1_000_000.0);
        if (value >= 1_000L) return "%.1fk".formatted(value / 1_000.0);
        return Long.toString(value);
    }

    private static String duration(long nanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(Math.max(0, nanos));
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
}
