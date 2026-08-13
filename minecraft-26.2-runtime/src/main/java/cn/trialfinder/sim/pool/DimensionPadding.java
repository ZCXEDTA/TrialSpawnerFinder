package cn.trialfinder.sim.pool;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.structure.pools.DimensionPadding}（26.2 语义）。
 * trial_chambers 使用 bottom=10, top=10（来自 structure JSON "dimension_padding": 10）。
 */
public record DimensionPadding(int bottom, int top) {
    public static final DimensionPadding ZERO = new DimensionPadding(0, 0);
}
