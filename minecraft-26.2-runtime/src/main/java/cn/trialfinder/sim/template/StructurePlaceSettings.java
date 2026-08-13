package cn.trialfinder.sim.template;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Rotation;

/**
 * 复刻 {@code StructurePlaceSettings}（26.2 语义）—— 模拟使用的子集。
 * jigsaw 拼接恒为 RIGID、无镜像，只有旋转。
 */
public class StructurePlaceSettings {
    private Rotation rotation = Rotation.NONE;
    private BlockPos rotationPivot = BlockPos.ZERO;

    public StructurePlaceSettings setRotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public StructurePlaceSettings setRotationPivot(BlockPos pivot) {
        this.rotationPivot = pivot;
        return this;
    }

    public BlockPos getRotationPivot() {
        return this.rotationPivot;
    }
}
