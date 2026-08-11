package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.structure.pools.Projection;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.JigsawJunction (1.21.11).
 */
public class JigsawJunction {
    private final int sourceX;
    private final int sourceGroundY;
    private final int sourceZ;
    private final int deltaY;
    private final Projection destProjection;

    public JigsawJunction(int sourceX, int sourceGroundY, int sourceZ, int deltaY, Projection destProjection) {
        this.sourceX = sourceX;
        this.sourceGroundY = sourceGroundY;
        this.sourceZ = sourceZ;
        this.deltaY = deltaY;
        this.destProjection = destProjection;
    }

    public int getSourceX() {
        return this.sourceX;
    }

    public int getSourceGroundY() {
        return this.sourceGroundY;
    }

    public int getSourceZ() {
        return this.sourceZ;
    }

    public int getDeltaY() {
        return this.deltaY;
    }

    public Projection getDestProjection() {
        return this.destProjection;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JigsawJunction that)) {
            return false;
        }
        return this.sourceX == that.sourceX
                && this.sourceZ == that.sourceZ
                && this.deltaY == that.deltaY
                && this.destProjection == that.destProjection;
    }

    @Override
    public int hashCode() {
        int result = this.sourceX;
        result = 31 * result + this.sourceGroundY;
        result = 31 * result + this.sourceZ;
        result = 31 * result + this.deltaY;
        result = 31 * result + this.destProjection.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "JigsawJunction{sourceX=" + this.sourceX
                + ", sourceGroundY=" + this.sourceGroundY
                + ", sourceZ=" + this.sourceZ
                + ", deltaY=" + this.deltaY
                + ", destProjection=" + this.destProjection + "}";
    }
}
