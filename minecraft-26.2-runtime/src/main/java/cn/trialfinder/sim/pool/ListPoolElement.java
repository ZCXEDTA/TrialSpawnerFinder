package cn.trialfinder.sim.pool;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.BoundingBox;
import cn.trialfinder.sim.math.Rotation;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.template.JigsawBlockInfo;
import cn.trialfinder.sim.template.StructureTemplateManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 复刻 {@code ListPoolElement}（26.2 语义）。
 */
public class ListPoolElement extends StructurePoolElement {
    private final List<StructurePoolElement> elements;

    public ListPoolElement(List<StructurePoolElement> elements, Projection projection) {
        super(projection);
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("elements 为空");
        }
        this.elements = elements;
        this.setProjectionOnEachElement(projection);
    }

    @Override
    public Vec3i getSize(StructureTemplateManager manager, Rotation rotation) {
        int x = 0;
        int y = 0;
        int z = 0;
        for (StructurePoolElement element : this.elements) {
            Vec3i size = element.getSize(manager, rotation);
            x = Math.max(x, size.getX());
            y = Math.max(y, size.getY());
            z = Math.max(z, size.getZ());
        }
        return new Vec3i(x, y, z);
    }

    @Override
    public List<JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation, RandomSource random) {
        return this.elements.get(0).getShuffledJigsawBlocks(manager, pos, rotation, random);
    }

    @Override
    public BoundingBox getBoundingBox(
            StructureTemplateManager manager, BlockPos pos, Rotation rotation) {
        BoundingBox result = null;
        for (StructurePoolElement element : this.elements) {
            if (element != EmptyPoolElement.INSTANCE) {
                BoundingBox box = element.getBoundingBox(manager, pos, rotation);
                result = result == null ? box : result.encapsulate(new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
            }
        }
        if (result == null) {
            throw new IllegalStateException("无法计算 ListPoolElement 的包围盒");
        }
        return result;
    }

    @Override
    public StructurePoolElement setProjection(Projection projection) {
        super.setProjection(projection);
        this.setProjectionOnEachElement(projection);
        return this;
    }

    private void setProjectionOnEachElement(Projection projection) {
        this.elements.forEach(element -> element.setProjection(projection));
    }

    public List<StructurePoolElement> getElements() {
        return this.elements;
    }

    @Override
    public String toString() {
        return "List[" + this.elements.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }
}
