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
import java.util.concurrent.TimeUnit;

public final class ConsoleProgressReporter implements ProgressReporter {
    private static final long MAX_SILENCE_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final PrintStream output;
    private final Map<String, PhaseState> phases = new HashMap<>();
    private int previousWidth;

    public ConsoleProgressReporter() {
        this(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
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
        if (update.total() == 0) return;
        long now = System.nanoTime();
        PhaseState state = phases.get(update.phase());
        if (state == null || update.completed() < state.lastCompleted) {
            state = new PhaseState(now);
            phases.put(update.phase(), state);
        }
        int percent = (int) (update.completed() * 100 / update.total());
        boolean firstReport = state.lastReportedNanos == 0;
        boolean timeDue = now - state.lastReportedNanos >= MAX_SILENCE_NANOS;
        if (update.completed() != update.total()
                && !firstReport && percent < state.nextPercent && !timeDue) return;

        long elapsedNanos = update.elapsedNanos() >= 0
                ? update.elapsedNanos() : now - state.startedNanos;
        String line = update.hasEstimatedWork()
                ? ProgressFormatter.estimatedWork(
                        update.phase(), update.completed(), update.total(), update.processed(),
                        update.estimatedWork(), update.unit(), elapsedNanos)
                : ProgressFormatter.phase(
                        update.phase(), update.completed(), update.total(),
                        update.unit(), elapsedNanos);
        if (details != null && !details.isBlank()) {
            line += " | " + details;
        }
        output.print('\r');
        output.print(line);
        if (line.length() < previousWidth) {
            output.print(" ".repeat(previousWidth - line.length()));
            output.print('\r');
            output.print(line);
        }
        if (update.completed() == update.total()) {
            output.println();
            previousWidth = 0;
        } else {
            output.flush();
            previousWidth = line.length();
        }
        state.lastReportedNanos = now;
        state.lastCompleted = update.completed();
        state.nextPercent = percent + 1;
    }

    private static final class PhaseState {
        private final long startedNanos;
        private long lastReportedNanos;
        private long lastCompleted;
        private int nextPercent = 1;

        private PhaseState(long startedNanos) {
            this.startedNanos = startedNanos;
        }
    }
}
