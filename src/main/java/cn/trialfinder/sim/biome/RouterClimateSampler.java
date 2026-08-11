package cn.trialfinder.sim.biome;

import cn.trialfinder.sim.biome.noise.OverworldNoiseRouter;

/**
 * A {@link ClimateSampler} backed by the ported {@link OverworldNoiseRouter}.
 *
 * <p>The game samples the climate router at the chunk's start block position with y = 0 (the
 * surface climate slice). Because the router's four spline dimensions (continentalness / erosion /
 * depth / weirdness) are not yet exact, {@link #isAvailable()} reflects the router's
 * {@link OverworldNoiseRouter#isComplete()}, which is currently {@code false} — so
 * {@link BiomeChecker} will not silently filter on approximate values.
 */
public final class RouterClimateSampler implements ClimateSampler {

    private final OverworldNoiseRouter router;

    public RouterClimateSampler(OverworldNoiseRouter router) {
        this.router = router;
    }

    @Override
    public ClimateSample sample(int quartX, int quartY, int quartZ) {
        // quart -> block (x4), sample the climate router at y=0.
        double[] values = this.router.router().sample(quartX * 4.0, 0.0, quartZ * 4.0);
        return new ClimateSample(
                (float) values[0], (float) values[1], (float) values[2],
                (float) values[3], (float) values[4], (float) values[5]);
    }

    @Override
    public boolean isAvailable() {
        return this.router.isComplete();
    }
}
