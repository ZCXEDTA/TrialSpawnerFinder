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
import cn.trialfinder.sim.structure.StructurePlaceSettings;
import cn.trialfinder.sim.structure.StructureProcessorList;
import cn.trialfinder.sim.structure.StructureTemplate;
import cn.trialfinder.sim.structure.StructureTemplateManager;
import cn.trialfinder.sim.util.Util;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement (1.21.11).
 * Jigsaw shuffle order (shuffle then stable sort by descending selection priority) is preserved.
 */
public class SinglePoolElement extends StructurePoolElement {
    private static final Comparator<JigsawBlockInfo> HIGHEST_SELECTION_PRIORITY_FIRST =
            Comparator.comparingInt(JigsawBlockInfo::selectionPriority).reversed();

    protected final Identifier templateId;
    protected final Holder<StructureProcessorList> processors;
    protected final Optional<LiquidSettings> overrideLiquidSettings;

    protected SinglePoolElement(Identifier templateId, Holder<StructureProcessorList> processors,
                                Projection projection, Optional<LiquidSettings> liquidSettings) {
        super(projection);
        this.templateId = templateId;
        this.processors = processors;
        this.overrideLiquidSettings = liquidSettings;
    }

    public StructureTemplate getTemplate(StructureTemplateManager manager) {
        return manager.getOrCreate(this.templateId);
    }

    public cn.trialfinder.sim.resources.Identifier getTemplateLocation() {
        return this.templateId;
    }

    @Override
    public Vec3i getSize(StructureTemplateManager manager, Rotation rotation) {
        return this.getTemplate(manager).getSize(rotation);
    }

    @Override
    public List<JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation, RandomSource random) {
        List<JigsawBlockInfo> list = this.getTemplate(manager).getJigsaws(pos, rotation);
        Util.shuffle(list, random);
        sortBySelectionPriority(list);
        return list;
    }

    static void sortBySelectionPriority(List<JigsawBlockInfo> list) {
        list.sort(HIGHEST_SELECTION_PRIORITY_FIRST);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager manager, BlockPos pos, Rotation rotation) {
        StructureTemplate template = this.getTemplate(manager);
        return template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), pos);
    }

    @Override
    public String toString() {
        return "Single[" + this.templateId + "]";
    }
}
