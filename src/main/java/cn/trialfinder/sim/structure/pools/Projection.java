package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.util.StringRepresentable;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool$Projection (1.21.11).
 * Trial chambers use RIGID exclusively.
 */
public enum Projection implements StringRepresentable {
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
        throw new IllegalArgumentException("Unknown projection: " + name);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
