package cn.trialfinder.sim.structure.pools.alias;

import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.pools.StructureTemplatePool;

import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.alias.DirectPoolAlias (1.21.11).
 */
public record DirectPoolAlias(ResourceKey<StructureTemplatePool> alias, ResourceKey<StructureTemplatePool> target)
        implements PoolAliasBinding {

    @Override
    public void forEachResolved(RandomSource random,
                                BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> consumer) {
        consumer.accept(this.alias, this.target);
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return Stream.of(this.target);
    }
}
