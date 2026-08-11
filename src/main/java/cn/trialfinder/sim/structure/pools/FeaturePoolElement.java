package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.structure.BoundingBox;
import cn.trialfinder.sim.structure.JigsawBlockInfo;
import cn.trialfinder.sim.structure.Rotation;
import cn.trialfinder.sim.structure.StructureTemplateManager;

import java.util.List;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement (1.21.11).
 * Trial chambers never use feature pool elements in the spawner-finding path; the template
 * accessors return empty/zero as the vanilla feature element does for size and jigsaws.
 */
public class FeaturePoolElement extends StructurePoolElement {
    private final Holder<Object> feature;

    protected FeaturePoolElement(Holder<Object> feature, Projection projection) {
        super(projection);
        this.feature = feature;
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
        return new BoundingBox(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    @Override
    public String toString() {
        return "Feature[" + this.feature + "]";
    }
}
