package cn.trialfinder.sim.math;

/**
 * Port of the small subset of net.minecraft.util.Mth needed by the simulation (1.21.11).
 */
public final class Mth {
    private Mth() {
    }

    /**
     * net.minecraft.util.Mth.getSeed(int, int, int).
     * Reconstructed bit-exact from the compiled 1.21.11 class:
     * <pre>
     * long l = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
     * l = l * l * 42317861L + l * 11L;
     * return l >> 16;
     * </pre>
     */
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
