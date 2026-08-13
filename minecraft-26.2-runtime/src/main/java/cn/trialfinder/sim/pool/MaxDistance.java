package cn.trialfinder.sim.pool;

/**
 * 复刻 {@code JigsawStructure.MaxDistance}（26.2 语义）。
 */
public record MaxDistance(int horizontal, int vertical) {
    public MaxDistance(int value) {
        this(value, value);
    }
}
