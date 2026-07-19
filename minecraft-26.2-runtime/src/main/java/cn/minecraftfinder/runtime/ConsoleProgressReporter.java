package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ProgressFormatter;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class ConsoleProgressReporter implements ProgressReporter {
    private final PrintStream output;
    private long searchStartedNanos;
    private int nextPercent = 1;
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
        if (searchStartedNanos == 0) {
            searchStartedNanos = System.nanoTime();
        }
        // 粗筛数据由状态行显示；控制台只保留一条跨阶段的总进度条。
        if ("粗筛".equals(update.phase())) return;
        int percent = (int) (update.completed() * 100 / update.total());
        if (update.completed() != update.total() && percent < nextPercent) return;

        long elapsedNanos = System.nanoTime() - searchStartedNanos;
        String line = ProgressFormatter.phase(
                update.phase(), update.completed(), update.total(), update.unit(), elapsedNanos);
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
        nextPercent = percent + 1;
    }
}
