package cn.trialfinder.sim.noise;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.synth.SimplexNoise} 的梯度表（26.2 语义）。
 * {@link ImprovedNoise} 通过 {@link #dot(int[], double, double, double)} 使用它。
 */
public final class SimplexNoise {
    /** 官方梯度表（16 项，后 4 项为前向复制）。 */
    private static final int[][] GRADIENT = new int[][]{
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };

    private SimplexNoise() {
    }

    public static double dot(int[] gradient, double x, double y, double z) {
        return (double) gradient[0] * x + (double) gradient[1] * y + (double) gradient[2] * z;
    }

    public static int[] gradient(int hash) {
        return GRADIENT[hash & 15];
    }
}
