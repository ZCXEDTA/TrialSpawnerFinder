package cn.trialfinder.sim.structure.pools.alias;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.pools.StructureTemplatePool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup (1.21.11).
 *
 * <p>Important correction to the analysis report: {@code RandomSource.create(seed)} returns a
 * {@link LegacyRandomSource}, so the alias random stream is the Legacy LCG seeded by
 * {@code Mth.getSeed(startPos) ^ forkPositionalSeed}, NOT Xoroshiro. Bit-exact either way.
 */
@FunctionalInterface
public interface PoolAliasLookup {
    PoolAliasLookup EMPTY = key -> key;

    ResourceKey<StructureTemplatePool> lookup(ResourceKey<StructureTemplatePool> key);

    static PoolAliasLookup create(List<PoolAliasBinding> bindings, BlockPos startPos, long worldSeed) {
        if (bindings.isEmpty()) {
            return EMPTY;
        }
        RandomSource random = RandomSource.create(worldSeed).forkPositional().at(startPos);
        Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> map = new HashMap<>();
        bindings.forEach(binding -> binding.forEachResolved(random, map::put));
        return key -> map.getOrDefault(key, key);
    }
}
