package cn.trialfinder.sim.util;

/**
 * Simple pair, mirrors com.mojang.datafixers.util.Pair for the simulated code.
 */
public record Pair<A, B>(A first, B second) {
    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }
}
