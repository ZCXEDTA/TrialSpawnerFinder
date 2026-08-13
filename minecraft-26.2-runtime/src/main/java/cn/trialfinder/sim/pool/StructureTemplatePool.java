package cn.trialfinder.sim.pool;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Rotation;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resource.Holder;
import cn.trialfinder.sim.resource.Identifier;
import cn.trialfinder.sim.resource.ResourceKey;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.util.Pair;
import cn.trialfinder.sim.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 复刻 {@code StructureTemplatePool}（26.2 语义）。
 * 模板在构造时按权重展开，与 vanilla 一致。
 */
public class StructureTemplatePool {
    public static final ResourceKey<StructureTemplatePool> EMPTY_KEY =
            ResourceKey.create(Identifier.withDefaultNamespace("empty"));

    private final List<Pair<StructurePoolElement, Integer>> rawTemplates;
    private final List<StructurePoolElement> templates;
    private Holder<StructureTemplatePool> fallback;
    private int maxSize = Integer.MIN_VALUE;

    public StructureTemplatePool(Holder<StructureTemplatePool> fallback,
                                 List<Pair<StructurePoolElement, Integer>> templates) {
        this.rawTemplates = templates;
        this.templates = new ArrayList<>();
        for (Pair<StructurePoolElement, Integer> pair : templates) {
            StructurePoolElement element = pair.first();
            for (int i = 0; i < pair.second(); i++) {
                this.templates.add(element);
            }
        }
        this.fallback = fallback;
    }

    public int getMaxSize(StructureTemplateManager manager) {
        if (this.maxSize == Integer.MIN_VALUE) {
            this.maxSize = this.templates.stream()
                    .filter(element -> element != EmptyPoolElement.INSTANCE)
                    .mapToInt(element -> element.getBoundingBox(manager, BlockPos.ZERO, Rotation.NONE).getYSpan())
                    .max()
                    .orElse(0);
        }
        return this.maxSize;
    }

    public List<Pair<StructurePoolElement, Integer>> getTemplates() {
        return this.rawTemplates;
    }

    public Holder<StructureTemplatePool> getFallback() {
        return this.fallback;
    }

    void resolveFallback(StructureTemplatePool pool) {
        this.fallback = Holder.direct(pool);
    }

    public StructurePoolElement getRandomTemplate(RandomSource random) {
        return this.templates.isEmpty()
                ? EmptyPoolElement.INSTANCE
                : this.templates.get(random.nextInt(this.templates.size()));
    }

    public List<StructurePoolElement> getShuffledTemplates(RandomSource random) {
        return Util.shuffledCopy(this.templates, random);
    }

    public int size() {
        return this.templates.size();
    }
}
