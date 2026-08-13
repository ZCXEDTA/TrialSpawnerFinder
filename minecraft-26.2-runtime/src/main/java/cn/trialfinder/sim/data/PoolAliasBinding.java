package cn.trialfinder.sim.data;

import cn.trialfinder.sim.pool.StructureTemplatePool;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resource.ResourceKey;
import cn.trialfinder.sim.util.Weighted;
import cn.trialfinder.sim.util.WeightedList;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * 复刻 {@code PoolAliasBinding}（26.2 语义）。
 */
public interface PoolAliasBinding {

    void forEachResolved(
            RandomSource random,
            BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> consumer);

    Stream<ResourceKey<StructureTemplatePool>> allTargets();

    static DirectPoolAlias direct(String alias, String target) {
        return direct(key(alias), key(target));
    }

    static DirectPoolAlias direct(
            ResourceKey<StructureTemplatePool> alias, ResourceKey<StructureTemplatePool> target) {
        return new DirectPoolAlias(alias, target);
    }

    static RandomPoolAlias random(String alias, WeightedList<String> targets) {
        WeightedList.Builder<ResourceKey<StructureTemplatePool>> builder = WeightedList.builder();
        targets.unwrap().forEach(weighted -> builder.add(key(weighted.value()), weighted.weight()));
        return random(key(alias), builder.build());
    }

    static RandomPoolAlias random(
            ResourceKey<StructureTemplatePool> alias,
            WeightedList<ResourceKey<StructureTemplatePool>> targets) {
        return new RandomPoolAlias(alias, targets);
    }

    static RandomGroupPoolAlias randomGroup(WeightedList<List<PoolAliasBinding>> groups) {
        return new RandomGroupPoolAlias(groups);
    }

    static ResourceKey<StructureTemplatePool> key(String pathWithDefaultNamespace) {
        return ResourceKey.create(pathWithDefaultNamespace);
    }
}
