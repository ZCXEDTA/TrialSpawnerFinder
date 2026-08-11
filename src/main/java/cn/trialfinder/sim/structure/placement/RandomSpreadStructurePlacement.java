package cn.trialfinder.sim.structure.placement;

import cn.trialfinder.sim.math.ChunkPos;
import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.WorldgenRandom;

/**
 * Port of net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
 * (1.21.11). The Codec/registry machinery is dropped; only the placement algorithm is kept.
 *
 * <p>For trial chambers the data-driven values are spacing = 34, separation = 12,
 * spread_type = linear, salt = 94251327.
 */
public final class RandomSpreadStructurePlacement {
    private final int spacing;
    private final int separation;
    private final RandomSpreadType spreadType;
    private final int salt;

    public RandomSpreadStructurePlacement(int spacing, int separation, RandomSpreadType spreadType, int salt) {
        if (spacing <= separation) {
            throw new IllegalArgumentException("Spacing has to be larger than separation");
        }
        this.spacing = spacing;
        this.separation = separation;
        this.spreadType = spreadType;
        this.salt = salt;
    }

    public int spacing() {
        return this.spacing;
    }

    public int separation() {
        return this.separation;
    }

    public RandomSpreadType spreadType() {
        return this.spreadType;
    }

    public int salt() {
        return this.salt;
    }

    /**
     * getPotentialStructureChunk(worldSeed, chunkX, chunkZ): maps a chunk coordinate to the
     * potential structure chunk of its 34x34 region. Pure function of (seed, regionX, regionZ)
     * — stateless per region, hence trivially parallelizable.
     */
    public ChunkPos getPotentialStructureChunk(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, this.spacing);
        int regionZ = Math.floorDiv(chunkZ, this.spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(worldSeed, regionX, regionZ, this.salt);
        int range = this.spacing - this.separation;
        int offsetX = this.spreadType.evaluate(random, range);
        int offsetZ = this.spreadType.evaluate(random, range);
        return new ChunkPos(regionX * this.spacing + offsetX, regionZ * this.spacing + offsetZ);
    }

    /** Region-based variant — avoids the floorDiv round-trip for direct enumeration. */
    public ChunkPos getPotentialStructureChunkFromRegion(long worldSeed, int regionX, int regionZ) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(worldSeed, regionX, regionZ, this.salt);
        int range = this.spacing - this.separation;
        int offsetX = this.spreadType.evaluate(random, range);
        int offsetZ = this.spreadType.evaluate(random, range);
        return new ChunkPos(regionX * this.spacing + offsetX, regionZ * this.spacing + offsetZ);
    }

    /** isPlacementChunk(worldSeed, chunkX, chunkZ): true iff the chunk is its region's potential chunk. */
    public boolean isPlacementChunk(long worldSeed, int chunkX, int chunkZ) {
        ChunkPos potential = this.getPotentialStructureChunk(worldSeed, chunkX, chunkZ);
        return potential.x() == chunkX && potential.z() == chunkZ;
    }
}
