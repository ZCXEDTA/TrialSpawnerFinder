package cn.trialfinder.sim.biome.noise;

/**
 * Port of the climate subset of net.minecraft.world.level.levelgen.NoiseRouter (1.21.11).
 * Holds the 6 climate density functions; the full router has many more (terrain) fields.
 */
public record NoiseRouter(
        DensityFunction temperature,
        DensityFunction vegetation,       // humidity
        DensityFunction continentalness,
        DensityFunction erosion,
        DensityFunction depth,
        DensityFunction weirdness) {

    /** Samples the 6 climate dimensions at a block coordinate (before quart conversion). */
    public double[] sample(double x, double y, double z) {
        return new double[]{
                this.temperature.compute(x, y, z),
                this.vegetation.compute(x, y, z),
                this.continentalness.compute(x, y, z),
                this.erosion.compute(x, y, z),
                this.depth.compute(x, y, z),
                this.weirdness.compute(x, y, z)
        };
    }
}
