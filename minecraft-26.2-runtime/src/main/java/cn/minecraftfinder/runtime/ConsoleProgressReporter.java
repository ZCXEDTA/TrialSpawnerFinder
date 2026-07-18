package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ProgressFormatter;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;

public final class ConsoleProgressReporter implements ProgressReporter {
    private long searchStartedNanos;
    private int nextPercent = 1;

    @Override
    public synchronized void report(ProgressUpdate update) {
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
        System.out.println(line);
        nextPercent = percent + 1;
    }
}
