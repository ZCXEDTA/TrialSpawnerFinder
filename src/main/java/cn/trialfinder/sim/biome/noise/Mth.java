package cn.trialfinder.sim.biome.noise;

/**
 * Small subset of net.minecraft.util.Mth used by the noise primitives (1.21.11).
 */
public final class Mth {
    private Mth() {
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static double smoothstep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static double lerp2(double deltaX, double deltaY, double v00, double v10, double v01, double v11) {
        return lerp(deltaY, lerp(deltaX, v00, v10), lerp(deltaX, v01, v11));
    }

    public static double lerp3(double deltaX, double deltaY, double deltaZ,
                               double v000, double v100, double v010, double v110,
                               double v001, double v101, double v011, double v111) {
        return lerp(deltaZ,
                lerp2(deltaX, deltaY, v000, v100, v010, v110),
                lerp2(deltaX, deltaY, v001, v101, v011, v111));
    }
}
