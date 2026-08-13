package cn.trialfinder.sim.pool;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.BoundingBox;
import cn.trialfinder.sim.math.Rotation;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.template.JigsawBlockInfo;
import cn.trialfinder.sim.template.StructureTemplateManager;

import java.util.List;

/**
 * 复刻 {@code EmptyPoolElement}（26.2 语义）。
 */
public class EmptyPoolElement extends StructurePoolElement {
    public static final EmptyPoolElement INSTANCE = new EmptyPoolElement();

    private EmptyPoolElement() {
        super(Projection.TERRAIN_MATCHING);
    }

    @Override
    public Vec3i getSize(StructureTemplateManager manager, Rotation rotation) {
        return Vec3i.ZERO;
    }

    @Override
    public List<JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation, RandomSource random) {
        return List.of();
    }

    @Override
    public BoundingBox getBoundingBox(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation) {
        throw new IllegalStateException("EmptyPoolElement.getBoundingBox 无效调用，请先过滤");
    }

    @Override
    public String toString() {
        return "Empty";
    }
}
