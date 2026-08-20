package cn.trialfinder.sim.math;

import java.util.function.IntPredicate;

/**
 * 复刻 {@code net.minecraft.util.Mth} 中模拟需要的子集（26.2 语义）。
 * 全部公式经反编译字节码逐一对齐，浮点运算顺序与官方一致（不重排）。
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

    // ---- 通用数学（噪声 / 气候 / 样条用，26.2 语义） ----

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    public static int floor(float value) {
        return (int) Math.floor((double) value);
    }

    public static long lfloor(double value) {
        return (long) Math.floor(value);
    }

    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    public static double frac(double value) {
        return value - (double) lfloor(value);
    }

    public static double lerp(double v, double a, double b) {
        return a + v * (b - a);
    }

    public static float lerp(float v, float a, float b) {
        return a + v * (b - a);
    }

    public static double lerp2(double d1, double d2, double x0, double x1, double x2, double x3) {
        return lerp(d2, lerp(d1, x0, x1), lerp(d1, x2, x3));
    }

    public static double lerp3(double d1, double d2, double d3, double x0, double x1, double x2, double x3,
                               double x4, double x5, double x6, double x7) {
        return lerp(d3, lerp2(d1, d2, x0, x1, x2, x3), lerp2(d1, d2, x4, x5, x6, x7));
    }

    public static double inverseLerp(double v, double start, double end) {
        return (v - start) / (end - start);
    }

    public static double clampedLerp(double v, double start, double end) {
        return v < 0.0D ? start : v > 1.0D ? end : lerp(v, start, end);
    }

    public static double clampedMap(double v, double start, double end, double mapStart, double mapEnd) {
        return clampedLerp(inverseLerp(v, start, end), mapStart, mapEnd);
    }

    public static double smoothstep(double v) {
        return v * v * v * (v * (v * 6.0D - 15.0D) + 10.0D);
    }

    public static double smoothstepDerivative(double v) {
        return 30.0D * v * v * (v - 1.0D) * (v - 1.0D);
    }

    public static long square(long value) {
        return value * value;
    }

    public static double square(double value) {
        return value * value;
    }

    public static int square(int value) {
        return value * value;
    }

    public static float square(float value) {
        return value * value;
    }

    /** 返回第一个使 {@code predicate} 为 true 的索引，区间 [min, max)。 */
    public static int binarySearch(int min, int max, IntPredicate predicate) {
        int i = min - 1;
        while (max - i > 1) {
            int j = (i + max) >> 1;
            if (predicate.test(j)) {
                max = j;
            } else {
                i = j;
            }
        }
        return max;
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    /** 返回 a、b 中绝对值更大者；绝对值相同时倾向非负。 */
    public static int absMax(int a, int b) {
        int max = Math.max(a, b);
        return max >= 0 ? max : Math.min(a, b);
    }
}
