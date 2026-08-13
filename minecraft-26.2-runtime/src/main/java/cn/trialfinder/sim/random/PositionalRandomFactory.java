package cn.trialfinder.sim.random;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.resource.Identifier;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.PositionalRandomFactory}（26.2 语义）。
 */
public interface PositionalRandomFactory {

    default RandomSource at(BlockPos pos) {
        return this.at(pos.getX(), pos.getY(), pos.getZ());
    }

    default RandomSource fromHashOf(Identifier id) {
        return this.fromHashOf(id.toString());
    }

    RandomSource fromHashOf(String name);

    RandomSource fromSeed(long seed);

    RandomSource at(int x, int y, int z);

    void parityConfigString(StringBuilder builder);
}
