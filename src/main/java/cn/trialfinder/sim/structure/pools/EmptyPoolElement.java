package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.structure.BoundingBox;
import cn.trialfinder.sim.structure.JigsawBlockInfo;
import cn.trialfinder.sim.structure.Rotation;
import cn.trialfinder.sim.structure.StructureTemplateManager;

import java.util.List;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement (1.21.11).
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
    public BoundingBox getBoundingBox(StructureTemplateManager manager, BlockPos pos, Rotation rotation) {
        throw new IllegalStateException("Invalid call to EmptyPoolElement.getBoundingBox, filter me!");
    }

    @Override
    public String toString() {
        return "Empty";
    }
}
