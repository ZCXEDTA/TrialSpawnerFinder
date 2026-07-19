package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ProgressFormatter;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ProgressEventReporter implements ProgressReporter {
    private static final long MAX_SILENCE_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final String PROTOCOL_PREFIX = "@@MFP1|";

    private final PrintStream output;
    private final Map<String, PhaseState> phases = new HashMap<>();

    public ProgressEventReporter() {
        this(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
    }

    ProgressEventReporter(PrintStream output) {
        this.output = output;
    }

    @Override
    public synchronized void report(ProgressUpdate update) {
        report(update, "");
    }

    @Override
    public synchronized void report(ProgressUpdate update, String details) {
        if (update.total() == 0) return;
        long now = System.nanoTime();
        PhaseState state = phases.get(update.phase());
        if (state == null || update.completed() < state.lastCompleted
                || update.processed() < state.lastProcessed) {
            state = new PhaseState(now, update.processed());
            phases.put(update.phase(), state);
        }
        long progressValue = update.hasEstimatedWork()
                ? update.processed() : update.completed();
        long progressTotal = update.hasEstimatedWork()
                ? update.estimatedWork() : update.total();
        int percent = progressTotal == 0 ? 100
                : (int) Math.min(100, progressValue * 100 / progressTotal);
        boolean firstReport = state.lastReportedNanos == 0;
        boolean timeDue = now - state.lastReportedNanos >= MAX_SILENCE_NANOS;
        if (update.completed() != update.total()
                && !firstReport && percent < state.nextPercent && !timeDue) return;

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
        boolean complete = update.completed() == update.total();
        output.println(PROTOCOL_PREFIX + encode(update.phase()) + '|'
                + (complete ? '1' : '0') + '|' + encode(line));
        state.lastReportedNanos = now;
        state.lastCompleted = update.completed();
        state.lastProcessed = update.processed();
        state.nextPercent = percent + 1;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class PhaseState {
        private final long startedNanos;
        private final long initialProcessed;
        private long lastReportedNanos;
        private long lastCompleted;
        private long lastProcessed;
        private int nextPercent = 1;

        private PhaseState(long startedNanos, long initialProcessed) {
            this.startedNanos = startedNanos;
            this.initialProcessed = initialProcessed;
            this.lastProcessed = initialProcessed;
        }
    }
}
