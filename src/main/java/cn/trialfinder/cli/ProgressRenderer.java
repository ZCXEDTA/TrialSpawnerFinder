package cn.trialfinder.cli;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Renders a stage-labelled progress line:
 *
 * <pre>[A-Flow   ] [##########] 100% 1058/1058 | 87.2 座/秒 | ETA 00:00:00
 *[Density  ] [##########] 100% 1058/1058 | 152.3 座/秒 | ETA 00:00:00
 *[B-Flow   ] [####------]  40%  423/1058 | 12.5 座/秒 | ETA 00:00:45</pre>
 *
 * <p>Stage names are ASCII (English) so {@link String#format} {@code %-10s} alignment is exact
 * (every character is half-width). {@link #update} is thread-safe and rate-limited (at most one
 * refresh per {@link #MIN_UPDATE_INTERVAL_NANOS}), so parallel B-flow workers can report progress
 * without flooding the console. The final (done) line is always emitted with a trailing newline.
 */
public final class ProgressRenderer {

    /** Fixed display width of the stage column. */
    public static final int STAGE_WIDTH = 10;
    /** Minimum interval between partial-line refreshes (100 ms). */
    public static final long MIN_UPDATE_INTERVAL_NANOS = 100_000_000L;

    // Stage names (English / ASCII, all <= STAGE_WIDTH chars so %-10s aligns exactly).
    public static final String STAGE_A_FLOW = "A-Flow";
    public static final String STAGE_DENSITY = "Density";
    public static final String STAGE_B_FLOW = "B-Flow";
    public static final String STAGE_STAT = "Stat";
    public static final String STAGE_SORT = "Sort";
    public static final String STAGE_OUTPUT = "Output";

    // Full-world tile-progress stage names (CJK counts as two columns; displayWidth keeps the
    // stage column visually aligned with the ASCII stages above).
    public static final String STAGE_FULL_WORLD = "全图扫描";
    public static final String STAGE_GLOBAL_TOP_K = "全局Top-K";

    private volatile String currentStage = "Init";
    private volatile boolean quiet;
    private volatile long stageStartNanos = System.nanoTime();
    private final AtomicLong lastPrintNanos = new AtomicLong(0);

    public ProgressRenderer() {
    }

    /** Returns a renderer that does nothing (used by callers that don't want progress). */
    public static ProgressRenderer disabled() {
        ProgressRenderer renderer = new ProgressRenderer();
        renderer.quiet = true;
        return renderer;
    }

    /** Updates the current stage name and restarts its elapsed timer; the next {@link #update} uses it. */
    public void setStage(String stage) {
        this.currentStage = stage;
        this.stageStartNanos = System.nanoTime();
    }

    public String getStage() {
        return this.currentStage;
    }

    /** Enables/disables all console output (used by {@code --quiet}). */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public boolean isQuiet() {
        return this.quiet;
    }

    /**
     * Emits a 100% completion line for the current stage using the elapsed time since
     * {@link #setStage}. Used after a one-shot stage (A-flow, density, sort, output) completes so
     * the stage name is visible even though it never called incremental {@link #update}.
     */
    public void stageDone(int total) {
        if (this.quiet) {
            return;
        }
        double elapsed = Math.max(0.001, (System.nanoTime() - this.stageStartNanos) / 1_000_000_000.0);
        double rate = total / elapsed;
        update(this.currentStage, total, total, rate, "00:00:00");
    }

    /** Sets the stage and emits a progress update. */
    public void update(String stage, int current, int total, double rate, String eta) {
        this.currentStage = stage;
        update(current, total, rate, eta);
    }

    /**
     * Emits a progress update using the current stage. Thread-safe and rate-limited. The done
     * line ({@code current >= total}) always prints with a trailing newline.
     */
    public void update(int current, int total, double rate, String eta) {
        if (this.quiet) {
            return;
        }
        boolean done = current >= total;
        long now = System.nanoTime();
        long last = this.lastPrintNanos.get();
        if (!done && now - last < MIN_UPDATE_INTERVAL_NANOS) {
            return; // rate-limit partial refreshes
        }
        this.lastPrintNanos.set(now);
        String line = line(this.currentStage, current, total, rate, eta);
        if (done) {
            System.out.println(line);
        } else {
            System.out.print("\r" + line);
            System.out.flush();
        }
    }

    /** Builds a progress line (static, no I/O). */
    public static String line(String stage, int current, int total, double rate, String eta) {
        int percent = total == 0 ? 100 : (int) Math.round(current * 100.0 / total);
        int filled = total == 0 ? 10 : (int) ((long) current * 10 / total);
        String bar = "#".repeat(filled) + "-".repeat(10 - filled);
        return "[%s] [%s] %3d%% %d/%d | %.1f 座/秒 | ETA %s".formatted(
                padRight(stage, STAGE_WIDTH), bar, percent, current, total, rate, eta);
    }

    /**
     * Emits a full-world tile progress line (used by {@code --full-world} streaming and the global
     * Top-K accumulation phase). Rate-limited like {@link #update}; the done line prints with a
     * trailing newline.
     *
     * <p>Example:
     * <pre>[全局Top-K] [#####-----]  45% Tile 165,124/368,449 | 密室: 1,234,567 | 刷怪笼: 18,901 | 耗时: 01:23:45 | ETA: 01:40:55</pre>
     */
    public void updateTile(int current, int total, long totalChambers, long totalSpawners,
                           long elapsedNanos, String eta) {
        if (this.quiet) {
            return;
        }
        boolean done = current >= total;
        long now = System.nanoTime();
        long last = this.lastPrintNanos.get();
        if (!done && now - last < MIN_UPDATE_INTERVAL_NANOS) {
            return; // rate-limit partial refreshes
        }
        this.lastPrintNanos.set(now);
        String line = tileLine(this.currentStage, current, total, totalChambers, totalSpawners,
                elapsedNanos, eta);
        if (done) {
            System.out.println(line);
        } else {
            System.out.print("\r" + line);
            System.out.flush();
        }
    }

    /** Builds a full-world tile progress line (static, no I/O). */
    public static String tileLine(String stage, int current, int total,
                                  long totalChambers, long totalSpawners,
                                  long elapsedNanos, String eta) {
        boolean oneDecimal = total > 100_000;
        String percent;
        String bar;
        if (total == 0) {
            percent = oneDecimal ? "100.0%" : "100%";
            bar = "#".repeat(10);
        } else {
            double pct = current * 100.0 / total;
            percent = oneDecimal ? "%.1f%%".formatted(pct) : "%d%%".formatted((int) Math.round(pct));
            int filled = (int) ((long) current * 10 / total);
            bar = "#".repeat(filled) + "-".repeat(10 - filled);
        }
        return "[%s] [%s] %6s Tile %,d/%,d | 密室: %,d | 刷怪笼: %,d | 耗时: %s | ETA: %s".formatted(
                padRight(stage, STAGE_WIDTH), bar, percent, current, total,
                totalChambers, totalSpawners, formatDurationNanos(elapsedNanos), eta);
    }

    private static String padRight(String value, int width) {
        int used = displayWidth(value);
        int pad = Math.max(0, width - used);
        return value + " ".repeat(pad);
    }

    private static int displayWidth(String value) {
        return value.codePoints().map(codePoint -> codePoint <= 0x7f ? 1 : 2).sum();
    }

    /** Formats a nanosecond duration as HH:MM:SS. */
    public static String formatDurationNanos(long nanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(Math.max(0, nanos));
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
}
