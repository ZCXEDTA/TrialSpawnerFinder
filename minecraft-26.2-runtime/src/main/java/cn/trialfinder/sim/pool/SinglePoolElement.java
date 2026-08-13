package cn.trialfinder.sim.pool;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.BoundingBox;
import cn.trialfinder.sim.math.Rotation;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resource.Identifier;
import cn.trialfinder.sim.template.JigsawBlockInfo;
import cn.trialfinder.sim.template.StructurePlaceSettings;
import cn.trialfinder.sim.template.StructureTemplate;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.util.Util;

import java.util.Comparator;
import java.util.List;

/**
 * 复刻 {@code SinglePoolElement}（26.2 语义）。
 * Jigsaw 洗牌顺序（先洗牌，再按选择优先级降序稳定排序）保持不变。
 */
public class SinglePoolElement extends StructurePoolElement {
    private static final Comparator<JigsawBlockInfo> HIGHEST_SELECTION_PRIORITY_FIRST =
            Comparator.comparingInt(JigsawBlockInfo::selectionPriority).reversed();

    protected final Identifier templateId;

    public SinglePoolElement(Identifier templateId, Projection projection) {
        super(projection);
        this.templateId = templateId;
    }

    public StructureTemplate getTemplate(StructureTemplateManager manager) {
        return manager.getOrCreate(this.templateId);
    }

    public Identifier getTemplateLocation() {
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
    public BoundingBox getBoundingBox(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation) {
        StructureTemplate template = this.getTemplate(manager);
        return template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), pos);
    }

    @Override
    public String toString() {
        return "Single[" + this.templateId + "]";
    }
}
