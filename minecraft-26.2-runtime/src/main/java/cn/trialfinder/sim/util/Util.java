package cn.trialfinder.sim.util;

import cn.trialfinder.sim.random.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 复刻 {@code net.minecraft.util.Util} 中模拟需要的工具方法（26.2 语义）。
 * {@code shuffle}/{@code getRandom} 与原版逐位一致，保证 RNG 消费顺序。
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

    /** 原版从后往前的 Fisher–Yates 洗牌。 */
    public static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size(); i > 1; i--) {
            Collections.swap(list, i - 1, random.nextInt(i));
        }
    }
}
