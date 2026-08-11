package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.resources.Identifier;
import cn.trialfinder.sim.structure.BoundingBox;
import cn.trialfinder.sim.structure.JigsawBlockInfo;
import cn.trialfinder.sim.structure.LiquidSettings;
import cn.trialfinder.sim.structure.Rotation;
import cn.trialfinder.sim.structure.StructureProcessorList;
import cn.trialfinder.sim.structure.StructureTemplateManager;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement (1.21.11).
 * {@code place(...)} is intentionally absent: the simulation never writes to a world.
 */
public abstract class StructurePoolElement {
    private static final Holder<StructureProcessorList> EMPTY_PROCESSORS = Holder.direct(StructureProcessorList.EMPTY);

    private volatile Projection projection;

    protected StructurePoolElement(Projection projection) {
        this.projection = projection;
    }

    public Projection getProjection() {
        Projection projection = this.projection;
        if (projection == null) {
            throw new IllegalStateException();
        }
        return projection;
    }

    public StructurePoolElement setProjection(Projection projection) {
        this.projection = projection;
        return this;
    }

    public abstract Vec3i getSize(StructureTemplateManager manager, Rotation rotation);

    public abstract List<JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation, RandomSource random);

    public abstract BoundingBox getBoundingBox(StructureTemplateManager manager, BlockPos pos, Rotation rotation);

    public int getGroundLevelDelta() {
        return 1;
    }

    /**
     * Visits every {@link SinglePoolElement} template id reachable from this element (recursing
     * through {@link ListPoolElement}). Used by {@code SimChamberGenerator} to preload all templates
     * so B-flow generation never performs a first-use template load (I/O + NBT parse) concurrently.
     */
    public void collectTemplateIds(java.util.function.Consumer<cn.trialfinder.sim.resources.Identifier> out) {
        if (this instanceof SinglePoolElement single) {
            out.accept(single.getTemplateLocation());
        } else if (this instanceof ListPoolElement list) {
            for (StructurePoolElement element : list.getElements()) {
                element.collectTemplateIds(out);
            }
        }
    }

    public static Function<Projection, EmptyPoolElement> empty() {
        return projection -> EmptyPoolElement.INSTANCE;
    }

    public static Function<Projection, SinglePoolElement> single(String templateId) {
        return projection -> new SinglePoolElement(Identifier.parse(templateId), EMPTY_PROCESSORS, projection, Optional.empty());
    }

    public static Function<Projection, SinglePoolElement> single(String templateId, Holder<StructureProcessorList> processors) {
        return projection -> new SinglePoolElement(Identifier.parse(templateId), processors, projection, Optional.empty());
    }

    public static Function<Projection, LegacySinglePoolElement> legacy(String templateId) {
        return projection -> new LegacySinglePoolElement(Identifier.parse(templateId), EMPTY_PROCESSORS, projection, Optional.empty());
    }

    public static Function<Projection, ListPoolElement> list(
            List<Function<Projection, ? extends StructurePoolElement>> elements) {
        return projection -> new ListPoolElement(
                elements.stream().map(function -> function.apply(projection)).collect(Collectors.toList()), projection);
    }

    protected static Holder<StructureProcessorList> emptyProcessors() {
        return EMPTY_PROCESSORS;
    }
}
