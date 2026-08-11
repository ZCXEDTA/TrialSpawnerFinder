package cn.trialfinder.accel;

import cn.trialfinder.model.BlockPoint;

import java.util.List;

/**
 * Abstraction over the two GPU-accelerable stages. The CPU implementation is the ground-truth
 * reference (matches the validated 1.21.11 pipeline); a GPU implementation must produce
 * bit-identical results or the CLI falls back to CPU.
 */
public interface Accelerator {

    /**
     * Enumerates candidate trial-chamber block points (chunk*16+8) whose block position lies
     * within {@code [minX,maxX]x[minZ,maxZ]} and, when {@code circleFilter} is set, within the
     * circle centered at {@code (centerX,centerZ)} of squared radius {@code radiusSq}.
     * Returns a sorted, de-duplicated list.
     */
    List<BlockPoint> findChunks(long seed, long minX, long maxX, long minZ, long maxZ,
                                boolean circleFilter, int centerX, int centerZ, long radiusSq);

    /**
     * Lossless density pre-filter: for each candidate, counts neighbours within
     * {@code 2*clusterRadius} (Euclidean, including itself) and keeps those with a count
     * {@code >= minStructures}. Any candidate that can be a member of a qualifying cluster
     * (≥ minStructures structures within {@code clusterRadius} of a common centre) survives,
     * so pruning does not change the final cluster set.
     */
    boolean[] pruneByDensity(List<BlockPoint> candidates, int clusterRadius, int minStructures);

    /**
     * Coarse density score: for each candidate, the number of candidates within
     * {@code 2*clusterRadius} (Euclidean, including itself). Used by the top-K full-world path to
     * rank candidates and keep only the highest-scoring K before running expensive B-flow Jigsaw
     * generation. Returns one int per input candidate.
     */
    int[] densityScores(List<BlockPoint> candidates, int clusterRadius);

    /**
     * Grid-prefilter aggregation (used by {@code --prefilter-mode grid}): scores every candidate
     * with {@link #densityScores}, aggregates the total density score per grid cell of side
     * {@code gridSizeBlocks} (block units), keeps the {@code topK} cells with the highest total
     * score, and returns the candidates that fall into those cells (no truncation when
     * {@code topK} >= the number of occupied cells). Deterministic: ties broken by cell index.
     */
    List<BlockPoint> gridAggregateAndSelect(List<BlockPoint> candidates, int clusterRadius,
                                            int gridSizeBlocks, int topK);

    /**
     * GPU-direct grid prefilter: enumerates candidates and counts them per grid cell entirely on
     * the GPU, selects the top-K cells by count, and returns only the candidates inside those
     * cells — avoiding the host-side construction of millions of {@link BlockPoint} objects for
     * huge search radii. The CPU default enumerates all candidates then applies
     * {@link #gridAggregateAndSelect} (density-weighted); the GPU path is count-weighted, an
     * approximation consistent with the grid prefilter being approximate by design.
     */
    default List<BlockPoint> findChunksGridPrefiltered(
            long seed, long minX, long maxX, long minZ, long maxZ,
            boolean circleFilter, int centerX, int centerZ, long radiusSq,
            int clusterRadius, int gridSizeBlocks, int topK) {
        List<BlockPoint> all = findChunks(seed, minX, maxX, minZ, maxZ,
                circleFilter, centerX, centerZ, radiusSq);
        return gridAggregateAndSelect(all, clusterRadius, gridSizeBlocks, topK);
    }

    default boolean isGpu() {
        return false;
    }

    default String name() {
        return "cpu";
    }
}
