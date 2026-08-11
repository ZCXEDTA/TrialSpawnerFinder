package cn.trialfinder.sim.structure.pools;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.DimensionPadding (1.21.11).
 * trial_chambers uses bottom=10, top=10 (from structure JSON "dimension_padding": 10).
 */
public record DimensionPadding(int bottom, int top) {
    public static final DimensionPadding ZERO = new DimensionPadding(0, 0);
}
