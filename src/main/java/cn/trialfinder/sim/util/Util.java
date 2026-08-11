package cn.trialfinder.sim.util;

import cn.trialfinder.sim.random.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Port of the small subset of net.minecraft.util.Util used by the simulation (1.21.11).
 * shuffle/getRandom are bit-exact with the vanilla implementations.
 */
public final class Util {
    private Util() {
    }

    public static <T> T getRandom(T[] array, RandomSource random) {
        return array[random.nextInt(array.length)];
    }

    public static <T> T getRandom(List<T> list, RandomSource random) {
        return list.get(random.nextInt(list.size()));
    }

    public static <T> List<T> shuffledCopy(List<T> list, RandomSource random) {
        List<T> copy = new ArrayList<>(list);
        shuffle(copy, random);
        return copy;
    }

    /** Vanilla back-to-front Fisher–Yates shuffle. */
    public static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size(); i > 1; i--) {
            Collections.swap(list, i - 1, random.nextInt(i));
        }
    }
}
