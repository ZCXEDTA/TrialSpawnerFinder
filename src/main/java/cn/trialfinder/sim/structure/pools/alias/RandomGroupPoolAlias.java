package cn.trialfinder.sim.structure.pools.alias;

import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.pools.StructureTemplatePool;
import cn.trialfinder.sim.util.Weighted;
import cn.trialfinder.sim.util.WeightedList;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.alias.RandomGroupPoolAlias (1.21.11).
 */
public record RandomGroupPoolAlias(WeightedList<List<PoolAliasBinding>> groups) implements PoolAliasBinding {

    @Override
    public void forEachResolved(RandomSource random,
                                BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> consumer) {
        this.groups.getRandom(random).ifPresent(list -> list.forEach(binding -> binding.forEachResolved(random, consumer)));
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return this.groups.unwrap().stream()
                .flatMap(weighted -> weighted.value().stream())
                .flatMap(PoolAliasBinding::allTargets);
    }
}
