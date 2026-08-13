package cn.trialfinder.sim.data;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.pool.StructureTemplatePool;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resource.ResourceKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复刻 {@code PoolAliasLookup}（26.2 语义）。
 * 算法与官方一致：{@code RandomSource.create(worldSeed).forkPositional().at(startPos)}，
 * 生成别名 → 目标映射；{@code RandomSource.create} 返回 {@link cn.trialfinder.sim.random.LegacyRandomSource}（LCG）。
 */
@FunctionalInterface
public interface PoolAliasLookup {
    PoolAliasLookup EMPTY = key -> key;

    ResourceKey<StructureTemplatePool> lookup(ResourceKey<StructureTemplatePool> key);

    static PoolAliasLookup create(
            List<PoolAliasBinding> bindings, BlockPos startPos, long worldSeed) {
        if (bindings.isEmpty()) {
            return EMPTY;
        }
        RandomSource random = RandomSource.create(worldSeed).forkPositional().at(startPos);
        Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> map = new HashMap<>();
        bindings.forEach(binding -> binding.forEachResolved(random, map::put));
        return key -> map.getOrDefault(key, key);
    }
}
