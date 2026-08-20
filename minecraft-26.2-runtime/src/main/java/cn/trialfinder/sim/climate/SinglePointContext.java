package cn.trialfinder.sim.climate;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.SinglePointContext}（26.2 语义）：
 * 生物群系采样用的单点上下文，blendAlpha=1.0、blendOffset=0.0。
 */
public record SinglePointContext(int blockX, int blockY, int blockZ)
        implements DensityFunction.FunctionContext {

    @Override
    public double blendAlpha() {
        return 1.0D;
    }

    @Override
    public double blendOffset() {
        return 0.0D;
    }
}
