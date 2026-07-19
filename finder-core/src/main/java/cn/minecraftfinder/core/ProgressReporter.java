package cn.minecraftfinder.core;

@FunctionalInterface
public interface ProgressReporter {
    ProgressReporter NONE = update -> { };

    void report(ProgressUpdate update);

    default void report(ProgressUpdate update, String details) {
        report(update);
    }
}
