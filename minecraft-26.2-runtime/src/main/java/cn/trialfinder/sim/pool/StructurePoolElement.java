package cn.trialfinder.sim.pool;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.BoundingBox;
import cn.trialfinder.sim.math.Rotation;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resource.Identifier;
import cn.trialfinder.sim.template.JigsawBlockInfo;
import cn.trialfinder.sim.template.StructureTemplateManager;

import java.util.List;

/**
 * 复刻 {@code StructurePoolElement}（26.2 语义）。
 * 有意省略 {@code place(...)}——模拟从不写入世界。
 */
public abstract class StructurePoolElement {
    private volatile Projection projection;

    protected StructurePoolElement(Projection projection) {
        this.projection = projection;
    }

    public Projection getProjection() {
        Projection projection = this.projection;
        if (projection == null) {
            throw new IllegalStateException("projection 未初始化");
        }
        return projection;
    }

    public static Projection getRigidProjection() {
        return Projection.RIGID;
    }

    public StructurePoolElement setProjection(Projection projection) {
        this.projection = projection;
        return this;
    }

    public abstract Vec3i getSize(StructureTemplateManager manager, Rotation rotation);

    public abstract List<JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation, RandomSource random);

    public abstract BoundingBox getBoundingBox(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation);

    public int getGroundLevelDelta() {
        return 1;
    }

    /** 递归收集可达的模板 id（用于预加载所有模板）。 */
    public void collectTemplateIds(java.util.function.Consumer<Identifier> out) {
        if (this instanceof SinglePoolElement single) {
            out.accept(single.getTemplateLocation());
        } else if (this instanceof ListPoolElement list) {
            for (StructurePoolElement element : list.getElements()) {
                element.collectTemplateIds(out);
            }
        }
    }
}
