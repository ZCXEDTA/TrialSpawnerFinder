package cn.trialfinder.sim.pool;

/**
 * 复刻 {@code StructureTemplatePool.Projection}（26.2 语义）。
 * 试炼密室只用 RIGID。
 */
public enum Projection {
    RIGID("rigid"),
    TERRAIN_MATCHING("terrain_matching");

    private final String id;

    Projection(String id) {
        this.id = id;
    }

    public String getName() {
        return this.id;
    }

    public static Projection byName(String name) {
        for (Projection projection : values()) {
            if (projection.id.equals(name)) {
                return projection;
            }
        }
        throw new IllegalArgumentException("未知 projection: " + name);
    }

    public String getSerializedName() {
        return this.id;
    }
}
