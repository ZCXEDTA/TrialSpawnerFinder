package cn.trialfinder.sim.template;

/**
 * 复刻 {@code net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType}（26.2 语义）。
 */
public enum JointType {
    ROLLABLE("rollable"),
    ALIGNED("aligned");

    private final String name;

    JointType(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return this.name;
    }

    public static JointType byName(String name) {
        return switch (name) {
            case "rollable" -> ROLLABLE;
            case "aligned" -> ALIGNED;
            default -> throw new IllegalArgumentException("未知 joint 类型: " + name);
        };
    }
}
