package cn.trialfinder.sim.structure.pools;

/**
 * Port of net.minecraft.world.level.levelgen.structure.structures.JigsawStructure$MaxDistance (1.21.11).
 */
public record MaxDistance(int horizontal, int vertical) {
    public MaxDistance(int value) {
        this(value, value);
    }
}
