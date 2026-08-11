package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.util.StringRepresentable;

/**
 * Port of net.minecraft.world.level.block.entity.JigsawBlockEntity$JointType (1.21.11).
 */
public enum JointType implements StringRepresentable {
    ROLLABLE("rollable"),
    ALIGNED("aligned");

    private final String id;

    JointType(String id) {
        this.id = id;
    }

    public static JointType byName(String name) {
        for (JointType type : values()) {
            if (type.id.equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown joint type: " + name);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
