package cn.trialfinder.sim.util;

/** 复刻 {@code net.minecraft.util.random.Weighted}（26.2 语义）。 */
public record Weighted<T>(T value, int weight) {
    public static <T> Weighted<T> of(T value, int weight) {
        return new Weighted<>(value, weight);
    }
}
