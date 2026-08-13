package cn.trialfinder.sim.data;

import cn.trialfinder.sim.pool.StructureTemplatePool;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resource.ResourceKey;

import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * 复刻 {@code DirectPoolAlias}（26.2 语义）。
 */
public record DirectPoolAlias(
        ResourceKey<StructureTemplatePool> alias,
        ResourceKey<StructureTemplatePool> target) implements PoolAliasBinding {

    @Override
    public void forEachResolved(
            RandomSource random,
            BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> consumer) {
        consumer.accept(this.alias, this.target);
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return Stream.of(this.target);
    }
}
