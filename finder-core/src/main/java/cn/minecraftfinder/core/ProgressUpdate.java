package cn.minecraftfinder.core;

public record ProgressUpdate(
        String phase,
        long completed,
        long total,
        String unit,
        long processed,
        long estimatedWork,
        long elapsedNanos) {

    public ProgressUpdate(
            String phase, long completed, long total, String unit,
            long processed, long estimatedWork) {
        this(phase, completed, total, unit, processed, estimatedWork, -1);
    }

    public ProgressUpdate {
        if (completed < 0 || total < 0 || processed < 0 || estimatedWork < 0) {
            throw new IllegalArgumentException("进度数值不能小于 0");
        }
    }

    public static ProgressUpdate phase(
            String phase, long completed, long total, String unit) {
        return new ProgressUpdate(phase, completed, total, unit, completed, total);
    }

    public static ProgressUpdate estimated(
            String phase, long completed, long total, String unit,
            long processed, long estimatedWork) {
        return new ProgressUpdate(phase, completed, total, unit, processed, estimatedWork);
    }

    public static ProgressUpdate estimatedAt(
            String phase, long completed, long total, String unit,
            long processed, long estimatedWork, long elapsedNanos) {
        return new ProgressUpdate(
                phase, completed, total, unit, processed, estimatedWork, elapsedNanos);
    }

    public boolean hasEstimatedWork() {
        return processed != completed || estimatedWork != total;
    }
}
