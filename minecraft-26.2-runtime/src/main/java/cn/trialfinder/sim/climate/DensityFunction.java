package cn.trialfinder.sim.climate;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.DensityFunction}（26.2 语义）。
 * 只保留单点采样所需的 {@link #compute}；原版的 minValue/maxValue/缓存包装不进入采样路径，
 * 无需复刻。
 */
@FunctionalInterface
public interface DensityFunction {

    double compute(FunctionContext context);

    interface FunctionContext {
        int blockX();

        int blockY();

        int blockZ();

        /** 生物群系采样时恒为 1.0（无 blend 距离）。 */
        double blendAlpha();

        /** 生物群系采样时恒为 0.0。 */
        double blendOffset();
    }
}
