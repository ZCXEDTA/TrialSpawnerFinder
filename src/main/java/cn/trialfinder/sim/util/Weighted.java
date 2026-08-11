package cn.trialfinder.sim.util;

/**
 * Port of net.minecraft.util.random.Weighted (1.21.11).
 */
public record Weighted<T>(T value, int weight) {
    public static <T> Weighted<T> of(T value, int weight) {
        return new Weighted<>(value, weight);
    }
}
