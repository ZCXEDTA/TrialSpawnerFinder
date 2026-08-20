package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.noise.NormalNoise;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.DensityFunction$NoiseHolder}（26.2 语义）。
 * 持有已实例化的 {@link NormalNoise}；DF 中的 {@code "noise": "minecraft:xxx"} 与
 * {@code shift_a/shift_b} 的 {@code "argument": "minecraft:xxx"} 都解析为它。
 */
public final class NoiseHolder {
    private final NormalNoise noise;

    public NoiseHolder(NormalNoise noise) {
        this.noise = noise;
    }

    public double getValue(double x, double y, double z) {
        return this.noise == null ? 0.0D : this.noise.getValue(x, y, z);
    }

    public double maxValue() {
        return this.noise == null ? 2.0D : this.noise.maxValue();
    }
}
