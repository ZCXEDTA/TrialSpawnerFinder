package cn.trialfinder.sim.math;

/**
 * 复刻 {@code net.minecraft.util.Mth} 中模拟需要的子集（26.2 语义）。
 * {@link #getSeed} 用于 {@code PositionalRandomFactory.at}。
 */
public final class Mth {
    private Mth() {
    }

    /** 原版 {@code Mth.getSeed(int, int, int)}。 */
    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    public static long getSeed(Vec3i pos) {
        return getSeed(pos.getX(), pos.getY(), pos.getZ());
    }

    public static int floorDiv(int a, int b) {
        int q = a / b;
        return (a % b != 0 && (a ^ b) < 0) ? q - 1 : q;
    }

    public static long floorDiv(long a, long b) {
        long q = a / b;
        return (a % b != 0 && (a ^ b) < 0) ? q - 1 : q;
    }
}
