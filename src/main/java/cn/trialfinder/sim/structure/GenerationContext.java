package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.random.WorldgenRandom;
import cn.trialfinder.sim.structure.pools.PoolRegistry;

/**
 * Minimal replacement for net.minecraft.world.level.levelgen.structure.Structure$GenerationContext
 * (1.21.11). The {@code chunkGenerator} is intentionally absent: trial chambers use only RIGID
 * projection and never query terrain height.
 */
public record GenerationContext(
        StructureTemplateManager templateManager,
        PoolRegistry pools,
        WorldgenRandom random,
        long seed,
        HeightAccessor heightAccessor) {

    public record HeightAccessor(int minY, int height) {
        public int getMinY() {
            return this.minY;
        }

        public int getMaxY() {
            return this.minY + this.height - 1;
        }
    }
}
