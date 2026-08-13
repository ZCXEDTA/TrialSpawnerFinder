package cn.trialfinder.accel;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java ground-truth implementation of {@link Accelerator}. Uses the same 34×34 grid
 * placement and spatial-grid neighbour counting as the GPU kernels, so it serves as the
 * reference for correctness.
 */
public final class CpuAccelerator implements Accelerator {

    private static final int SPACING = TrialChambersData.SPACING_CHUNKS;
    /** Bounded spatial-hash cells in density scoring; oversized grids are coarsened losslessly. */
    private static final long MAX_DENSITY_CELLS = 10_000_000L;

    @Override
    public List<BlockPoint> findChunks(long seed, long minX, long maxX, long minZ, long maxZ,
                                       boolean circleFilter, int centerX, int centerZ, long radiusSq) {
        int minChunkX = Math.floorDiv(clampToInt(minX), 16);
        int maxChunkX = Math.floorDiv(clampToInt(maxX), 16);
        int minChunkZ = Math.floorDiv(clampToInt(minZ), 16);
        int maxChunkZ = Math.floorDiv(clampToInt(maxZ), 16);
        int minRegionX = Math.floorDiv(minChunkX, SPACING) - 1;
        int maxRegionX = Math.floorDiv(maxChunkX, SPACING) + 1;
        int minRegionZ = Math.floorDiv(minChunkZ, SPACING) - 1;
        int maxRegionZ = Math.floorDiv(maxChunkZ, SPACING) + 1;

        List<BlockPoint> result = new ArrayList<>();
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                ChunkPos chunk = TrialChambersData.PLACEMENT.getPotentialStructureChunkFromRegion(seed, regionX, regionZ);
                int x = chunk.x() * 16 + 8;
                int z = chunk.z() * 16 + 8;
                if (x < minX || x > maxX || z < minZ || z > maxZ) {
                    continue;
                }
                if (circleFilter) {
                    long dx = (long) x - centerX;
                    long dz = (long) z - centerZ;
                    if (dx * dx + dz * dz > radiusSq) {
                        continue;
                    }
                }
                result.add(new BlockPoint(x, z));
            }
        }
        result.sort(BlockPoint::compareTo);
        return result;
    }

    @Override
    public boolean[] pruneByDensity(List<BlockPoint> candidates, int clusterRadius, int minStructures) {
        int[] scores = densityScores(candidates, clusterRadius);
        boolean[] keep = new boolean[candidates.size()];
        for (int i = 0; i < keep.length; i++) {
            keep[i] = scores[i] >= minStructures;
        }
        return keep;
    }

    @Override
    public int[] densityScores(List<BlockPoint> candidates, int clusterRadius) {
        int n = candidates.size();
        int[] scores = new int[n];
        if (n == 0) {
            return scores;
        }
        // Neighbour criterion radius is fixed at 2R; the spatial hash cell is enlarged when the
        // grid would exceed MAX_DENSITY_CELLS. Coarser cells still cover every 2R neighbour in the
        // 3x3 neighbourhood, so the scores are unchanged.
        int cellSize = Math.max(1, clusterRadius * 2);
        long radiusSq = (long) cellSize * cellSize;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPoint p : candidates) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        long spanX = (long) maxX - minX;
        long spanZ = (long) maxZ - minZ;
        long cells = (spanX / cellSize + 1) * (spanZ / cellSize + 1);
        if (cells > MAX_DENSITY_CELLS) {
            long minCell = (long) Math.ceil(Math.max(spanX, spanZ) / Math.sqrt(MAX_DENSITY_CELLS));
            cellSize = Math.max(cellSize, (int) Math.min(Integer.MAX_VALUE, minCell));
        }
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < n; i++) {
            BlockPoint p = candidates.get(i);
            int cellX = Math.floorDiv(p.x(), cellSize);
            int cellZ = Math.floorDiv(p.z(), cellSize);
            grid.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < n; i++) {
            BlockPoint p = candidates.get(i);
            int cellX = Math.floorDiv(p.x(), cellSize);
            int cellZ = Math.floorDiv(p.z(), cellSize);
            int count = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int other : grid.getOrDefault(cellKey(cellX + dx, cellZ + dz), List.of())) {
                        BlockPoint q = candidates.get(other);
                        long ox = (long) p.x() - q.x();
                        long oz = (long) p.z() - q.z();
                        if (ox * ox + oz * oz <= radiusSq) {
                            count++;
                        }
                    }
                }
            }
            scores[i] = count;
        }
        return scores;
    }

    @Override
    public List<BlockPoint> gridAggregateAndSelect(List<BlockPoint> candidates, int clusterRadius,
                                                   int gridSizeBlocks, int topK) {
        int n = candidates.size();
        if (n == 0) {
            return new ArrayList<>();
        }
        int[] scores = densityScores(candidates, clusterRadius);

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPoint p : candidates) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        int gridDimX = (maxX - minX) / gridSizeBlocks + 1;
        int gridDimZ = (maxZ - minZ) / gridSizeBlocks + 1;
        long totalCells = (long) gridDimX * gridDimZ;
        if (totalCells > 10_000_000L) {
            throw new IllegalStateException("too many grid cells: " + totalCells);
        }

        int[] gridScores = new int[gridDimX * gridDimZ];
        int[] cellOf = new int[n];
        for (int i = 0; i < n; i++) {
            BlockPoint p = candidates.get(i);
            int gx = (p.x() - minX) / gridSizeBlocks;
            int gz = (p.z() - minZ) / gridSizeBlocks;
            int cell = gz * gridDimX + gx;
            cellOf[i] = cell;
            gridScores[cell] += scores[i];
        }

        // Select the top-K occupied cells: sort cell indices by (score desc, index asc).
        List<Integer> occupied = new ArrayList<>();
        for (int cell = 0; cell < gridScores.length; cell++) {
            if (gridScores[cell] > 0) {
                occupied.add(cell);
            }
        }
        occupied.sort((a, b) -> {
            int byScore = Integer.compare(gridScores[b], gridScores[a]);
            return byScore != 0 ? byScore : Integer.compare(a, b);
        });
        // topK <= 0 means no truncation: keep every occupied cell (lossless).
        int keepCells = topK <= 0 ? occupied.size() : Math.min(topK, occupied.size());
        java.util.Set<Integer> selected = new java.util.HashSet<>(occupied.subList(0, keepCells));

        List<BlockPoint> retained = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (selected.contains(cellOf[i])) {
                retained.add(candidates.get(i));
            }
        }
        retained.sort(BlockPoint::compareTo);
        return retained;
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffff_ffffL);
    }

    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
