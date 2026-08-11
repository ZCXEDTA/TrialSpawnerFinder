package cn.trialfinder.sim.structure.pools.alias;

import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.pools.StructureTemplatePool;
import cn.trialfinder.sim.util.Weighted;
import cn.trialfinder.sim.util.WeightedList;

import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.alias.RandomPoolAlias (1.21.11).
 */
public record RandomPoolAlias(ResourceKey<StructureTemplatePool> alias,
                              WeightedList<ResourceKey<StructureTemplatePool>> targets)
        implements PoolAliasBinding {

    @Override
    public void forEachResolved(RandomSource random,
                                BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> consumer) {
        this.targets.getRandom(random)
                .ifPresent(target -> consumer.accept(this.alias, target));
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return this.targets.unwrap().stream().map(Weighted::value);
    }
}
