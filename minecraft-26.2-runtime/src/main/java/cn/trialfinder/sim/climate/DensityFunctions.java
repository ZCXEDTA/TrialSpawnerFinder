package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.math.Mth;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.DensityFunctions} 中气候采样需要的子集
 * （26.2 语义）。只保留 compute 路径；flat_cache/cache_2d/cache_once 在单点采样下透明，
 * 由 {@link DensityFunctionCodec} 直接解开，无需包装。
 */
public final class DensityFunctions {
    private DensityFunctions() {
    }

    public static DensityFunction constant(double value) {
        return context -> value;
    }

    public static DensityFunction add(DensityFunction a, DensityFunction b) {
        return context -> a.compute(context) + b.compute(context);
    }

    public static DensityFunction mul(DensityFunction a, DensityFunction b) {
        return context -> a.compute(context) * b.compute(context);
    }

    public static DensityFunction abs(DensityFunction a) {
        return context -> Math.abs(a.compute(context));
    }

    public static DensityFunction yClampedGradient(
            double fromY, double toY, double fromValue, double toValue) {
        return context -> Mth.clampedMap(
                (double) context.blockY(), fromY, toY, fromValue, toValue);
    }

    public static DensityFunction spline(CubicSpline spline) {
        return context -> (double) spline.compute(context);
    }

    /**
     * 复刻 {@code ShiftedNoise.compute}：{@code x = blockX*xzScale + shiftX} 等，再查噪声。
     */
    public static DensityFunction shiftedNoise(
            NoiseHolder noise, DensityFunction shiftX, DensityFunction shiftY,
            DensityFunction shiftZ, double xzScale, double yScale) {
        return context -> {
            double x = (double) context.blockX() * xzScale + shiftX.compute(context);
            double y = (double) context.blockY() * yScale + shiftY.compute(context);
            double z = (double) context.blockZ() * xzScale + shiftZ.compute(context);
            return noise.getValue(x, y, z);
        };
    }

    /** 复刻 {@code ShiftA}：{@code shift(blockX, 0.0, blockZ)}。 */
    public static DensityFunction shiftA(NoiseHolder offsetNoise) {
        return context -> shift(offsetNoise, context.blockX(), 0.0D, context.blockZ());
    }

    /** 复刻 {@code ShiftB}：{@code shift(blockZ, blockX, 0.0)}。 */
    public static DensityFunction shiftB(NoiseHolder offsetNoise) {
        return context -> shift(offsetNoise, context.blockZ(), context.blockX(), 0.0D);
    }

    private static double shift(NoiseHolder offsetNoise, double x, double y, double z) {
        return offsetNoise.getValue(x * 0.25D, y * 0.25D, z * 0.25D) * 4.0D;
    }

    public static DensityFunction blendAlpha() {
        return DensityFunction.FunctionContext::blendAlpha;
    }

    public static DensityFunction blendOffset() {
        return DensityFunction.FunctionContext::blendOffset;
    }
}
