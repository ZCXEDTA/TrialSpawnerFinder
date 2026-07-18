package cn.minecraftfinder.core;

@FunctionalInterface
public interface ProgressReporter {
    ProgressReporter NONE = update -> { };

    void report(ProgressUpdate update);
}
