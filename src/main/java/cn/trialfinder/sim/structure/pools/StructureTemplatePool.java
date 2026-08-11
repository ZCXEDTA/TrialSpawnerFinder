package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.resources.Identifier;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.Rotation;
import cn.trialfinder.sim.structure.StructureTemplateManager;
import cn.trialfinder.sim.util.Pair;
import cn.trialfinder.sim.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool (1.21.11).
 * Templates are expanded by weight at construction, exactly as vanilla does.
 */
public class StructureTemplatePool {
    public static final ResourceKey<StructureTemplatePool> EMPTY_KEY = ResourceKey.create(Identifier.withDefaultNamespace("empty"));

    private final List<Pair<StructurePoolElement, Integer>> rawTemplates;
    private final List<StructurePoolElement> templates;
    private Holder<StructureTemplatePool> fallback;
    private int maxSize = Integer.MIN_VALUE;

    public StructureTemplatePool(Holder<StructureTemplatePool> fallback, List<Pair<StructurePoolElement, Integer>> templates) {
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

    public StructureTemplatePool(
            Holder<StructureTemplatePool> fallback,
            List<Pair<Function<Projection, ? extends StructurePoolElement>, Integer>> templates,
            Projection projection) {
        this.rawTemplates = new ArrayList<>();
        this.templates = new ArrayList<>();
        for (Pair<Function<Projection, ? extends StructurePoolElement>, Integer> pair : templates) {
            StructurePoolElement element = pair.first().apply(projection);
            this.rawTemplates.add(Pair.of(element, pair.second()));
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

    /** Resolves a reference fallback holder to a direct holder of the actual pool instance. */
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
