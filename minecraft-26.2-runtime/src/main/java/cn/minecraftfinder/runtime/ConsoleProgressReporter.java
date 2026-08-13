package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ProgressFormatter;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 纯 Java 命令行进度条渲染器，与旧项目 ProgressRenderer 一致：
 * <ul>
 *   <li>未完成时用 {@code \r} 原地刷新同一行，不换行（限流 100ms）；</li>
 *   <li>阶段完成时补换行——这是管道/重定向下唯一可见的行，所以不会刷屏；</li>
 *   <li>不依赖 PowerShell，cmd / PowerShell / Git Bash 下都能显示。</li>
 * </ul>
 */
public final class ConsoleProgressReporter implements ProgressReporter {
    private static final long MIN_UPDATE_INTERVAL_NANOS = 100_000_000L;

    private final PrintStream output;
    private final Map<String, PhaseState> phases = new HashMap<>();

    public ConsoleProgressReporter() {
        this(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
    }

    ConsoleProgressReporter(PrintStream output) {
        this.output = output;
    }

    @Override
    public synchronized void report(ProgressUpdate update) {
        report(update, "");
    }

    @Override
    public synchronized void report(ProgressUpdate update, String details) {
        if (update.total() == 0) {
            return;
        }
        long now = System.nanoTime();
        PhaseState state = phases.get(update.phase());
        if (state == null || update.completed() < state.lastCompleted
                || update.processed() < state.lastProcessed) {
            state = new PhaseState(now, update.processed());
            phases.put(update.phase(), state);
        }
        boolean complete = update.completed() == update.total();
        if (!complete && now - state.lastReportedNanos < MIN_UPDATE_INTERVAL_NANOS) {
            return; // 限流：未完成时最多每 100ms 刷新一次
        }
        state.lastReportedNanos = now;
        state.lastCompleted = update.completed();
        state.lastProcessed = update.processed();

        long elapsedNanos = update.elapsedNanos() >= 0
                ? update.elapsedNanos() : now - state.startedNanos;
        String line = update.hasEstimatedWork()
                ? ProgressFormatter.estimatedWork(
                        update.phase(), update.completed(), update.total(), update.processed(),
                        update.estimatedWork(), update.unit(),
                        state.initialProcessed, elapsedNanos)
                : ProgressFormatter.phase(
                        update.phase(), update.completed(), update.total(),
                        update.unit(), elapsedNanos);
        if (details != null && !details.isBlank()) {
            line += " | " + details;
        }
        // 与旧项目一致：未完成 \r 原地刷新；完成补换行（管道下只有完成行可见）。
        if (complete) {
            output.println(line);
        } else {
            output.print("\r" + line);
            output.flush();
        }
    }

    /** 结束当前阶段进度行（搜索收尾前调用，补一个换行让后续输出另起一行）。 */
    public synchronized void clearLine() {
        output.println();
    }

    private static final class PhaseState {
        private final long startedNanos;
        private final long initialProcessed;
        private long lastReportedNanos;
        private long lastCompleted;
        private long lastProcessed;

        private PhaseState(long startedNanos, long initialProcessed) {
            this.startedNanos = startedNanos;
            this.initialProcessed = initialProcessed;
            this.lastReportedNanos = startedNanos;
            this.lastProcessed = initialProcessed;
        }
    }
}
