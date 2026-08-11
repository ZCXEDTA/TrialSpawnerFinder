package cn.trialfinder.cli;

import cn.trialfinder.accel.Accelerator;
import cn.trialfinder.config.AreaShape;
import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.model.SpawnerPoint;
import cn.trialfinder.search.CircleClusters;
import cn.trialfinder.search.ExactCenterOptimizer;
import cn.trialfinder.sim.SimChamberGenerator;
import cn.trialfinder.sim.math.BlockPos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Core search pipeline, structurally identical to the validated {@code FinderSearch}:
 * <ol>
 *   <li><b>A flow</b> — enumerate candidate chambers via the 34×34 grid (accelerator), tiled;</li>
 *   <li>lossless density pre-filter (accelerator) — prunes candidates that cannot be members of a
 *       qualifying cluster;</li>
 *   <li>cluster candidates with {@link CircleClusters} (CPU), keep owned clusters;</li>
 *   <li><b>B flow</b> — generate each cluster's chambers in parallel (CPU) and collect spawners;</li>
 *   <li>per-cluster density via {@link ExactCenterOptimizer} (CPU) — the server-validated scoring;</li>
 *   <li>filter, sort, truncate to 100 per structure-count group, write CSV + TXT.</li>
 * </ol>
 */
public final class SearchEngine {

    /** CLI configuration. */
    public record Options(
            long seed,
            int searchRadius,
            int clusterRadius,
            int minStructures,
            int minSpawners,
            boolean fullWorld,
            int threads,
            boolean debug,
            int tileSize,
            int tileOverlap,
            String clusterMethod,
            int maxClusterSize,
            int topK,
            String prefilterMode,
            int gridSize,
            SpawnerCache cache,
            int minCandidatesPerTile,
            int jigsawDepth) {

        /** Backward-compatible constructor (defaults tileSize=100000, tileOverlap=1000, density, 0=auto, no cache). */
        public Options(long seed, int searchRadius, int clusterRadius, int minStructures,
                       int minSpawners, boolean fullWorld, int threads, boolean debug) {
            this(seed, searchRadius, clusterRadius, minStructures, minSpawners,
                    fullWorld, threads, debug, 100_000, 1_000, "density", 0, 0, "cluster", 0, null, 0, 0);
        }

        /** Backward-compatible constructor with tile settings (density, 0=auto maxClusterSize, no cache). */
        public Options(long seed, int searchRadius, int clusterRadius, int minStructures,
                       int minSpawners, boolean fullWorld, int threads, boolean debug,
                       int tileSize, int tileOverlap) {
            this(seed, searchRadius, clusterRadius, minStructures, minSpawners,
                    fullWorld, threads, debug, tileSize, tileOverlap, "density", 0, 0, "cluster", 0, null, 0, 0);
        }

        /** Backward-compatible constructor with cluster settings (cluster prefilter default, no cache). */
        public Options(long seed, int searchRadius, int clusterRadius, int minStructures,
                       int minSpawners, boolean fullWorld, int threads, boolean debug,
                       int tileSize, int tileOverlap, String clusterMethod, int maxClusterSize) {
            this(seed, searchRadius, clusterRadius, minStructures, minSpawners,
                    fullWorld, threads, debug, tileSize, tileOverlap, clusterMethod, maxClusterSize,
                    0, "cluster", 0, null, 0, 0);
        }

        /** Backward-compatible constructor without cache (15-arg, the old canonical form). */
        public Options(long seed, int searchRadius, int clusterRadius, int minStructures,
                       int minSpawners, boolean fullWorld, int threads, boolean debug,
                       int tileSize, int tileOverlap, String clusterMethod, int maxClusterSize,
                       int topK, String prefilterMode, int gridSize) {
            this(seed, searchRadius, clusterRadius, minStructures, minSpawners,
                    fullWorld, threads, debug, tileSize, tileOverlap, clusterMethod, maxClusterSize,
                    topK, prefilterMode, gridSize, null, 0, 0);
        }

        /** Returns a copy with a shallow-jigsaw depth set (positive truncates decorative recursion). */
        public Options withJigsawDepth(int jigsawDepth) {
            return new Options(seed, searchRadius, clusterRadius, minStructures, minSpawners,
                    fullWorld, threads, debug, tileSize, tileOverlap, clusterMethod, maxClusterSize,
                    topK, prefilterMode, gridSize, cache, minCandidatesPerTile, jigsawDepth);
        }

        /** Effective maxClusterSize: 0 means auto (max(200, totalCandidates/10)). */
        public int effectiveMaxClusterSize(long totalCandidates) {
            return this.maxClusterSize > 0
                    ? this.maxClusterSize
                    : (int) Math.max(200, totalCandidates / 10);
        }

        /** Effective grid cell size in blocks for {@code --prefilter-mode grid}: 0 = 2*clusterRadius. */
        public int effectiveGridSize() {
            return this.gridSize > 0 ? this.gridSize : 2 * this.clusterRadius;
        }

        /** True when the GPU grid-aggregation prefilter is requested (and top-K is set). */
        public boolean isGridPrefilter() {
            return "grid".equalsIgnoreCase(this.prefilterMode) && this.topK > 0;
        }

        /**
         * Effective minimum candidate count below which a tile/region skips coarse clustering.
         * A candidate with density score &lt; {@code minStructures} cannot be a member of a qualifying
         * cluster (its 2R neighbourhood holds fewer than minStructures points), so pruning to
         * {@code score >= minStructures} is lossless; 0 means auto (= minStructures).
         */
        public int effectiveMinCandidatesPerTile() {
            return this.minCandidatesPerTile > 0 ? this.minCandidatesPerTile : this.minStructures;
        }
    }

    /** Outcome of a search. */
    public record Result(
            int candidateCount,
            int prunedCount,
            int clusterCount,
            int resultCount,
            int totalSpawners,
            List<SearchResult> results) {
    }

    /** Per-tile aggregate counters for progress reporting. */
    public static final class RegionStats {
        public long candidateCount;
        public long chamberCount;
        public long spawnerCount;
    }

    private static final int WORLD_LIMIT = 30_000_000;
    private static final long TILE_SIZE = 262_144;
    /** Candidate count above which a single-region grid search warns that the radius is excessive. */
    private static final long HUGE_CANDIDATE_WARN = 50_000_000L;
    /** Target candidates per automatic tile (bounded per-tile work/memory). */
    private static final long TARGET_TILE_CANDIDATES = 5_000_000L;
    /** Blocks per 34x34-chunk region (one chamber per region). */
    private static final double CHAMBER_AREA_BLOCKS = (34.0 * 16.0) * (34.0 * 16.0);
    /** Upper bound for the automatic grid-tile side (blocks) — keeps GPU buffers reasonable. */
    private static final long MAX_GRID_TILE_SIZE = 3_000_000L;

    /**
     * Estimates the number of trial-chamber candidates (one per 34x34-chunk region) that fall
     * inside the search area. Used to size automatic tiles and warn about impractical radii.
     */
    static long estimateCandidates(long searchMinX, long searchMaxX, long searchMinZ, long searchMaxZ,
                                   boolean circle, long radiusSq) {
        double area;
        if (circle) {
            area = Math.PI * radiusSq;
        } else {
            area = ((double) searchMaxX - searchMinX + 1) * ((double) searchMaxZ - searchMinZ + 1);
        }
        return (long) Math.ceil(area / CHAMBER_AREA_BLOCKS);
    }

    private SearchEngine() {
    }

    public static Result run(Options opts, Accelerator acc, PrintStream out) throws IOException {
        return run(opts, acc, out, ProgressRenderer.disabled());
    }

    /** Runs the full single-region pipeline, reporting stages via {@code progress}. */
    public static Result run(Options opts, Accelerator acc, PrintStream out, ProgressRenderer progress)
            throws IOException {
        return run(opts, acc, out, progress, null);
    }

    /** {@link #run(Options, Accelerator, PrintStream, ProgressRenderer)} with an optional biome filter. */
    public static Result run(Options opts, Accelerator acc, PrintStream out, ProgressRenderer progress,
                             cn.trialfinder.sim.biome.BiomeChecker biomeChecker) throws IOException {
        long started = System.nanoTime();

        // Search rectangle + circle filter.
        boolean circle = !opts.fullWorld();
        int centerX = 0;
        int centerZ = 0;
        long searchMinX = opts.fullWorld() ? -WORLD_LIMIT : Math.max(-WORLD_LIMIT, (long) centerX - opts.searchRadius());
        long searchMaxX = opts.fullWorld() ? WORLD_LIMIT : Math.min(WORLD_LIMIT, (long) centerX + opts.searchRadius());
        long searchMinZ = opts.fullWorld() ? -WORLD_LIMIT : Math.max(-WORLD_LIMIT, (long) centerZ - opts.searchRadius());
        long searchMaxZ = opts.fullWorld() ? WORLD_LIMIT : Math.min(WORLD_LIMIT, (long) centerZ + opts.searchRadius());
        long radiusSq = (long) opts.searchRadius() * opts.searchRadius();
        long margin = 2L * opts.clusterRadius() + 2;

        SimChamberGenerator generator = newGenerator(opts);
        long tGen = System.nanoTime();

        // ---------------------------------------------------------------- A flow (tiled)
        Map<List<BlockPoint>, CircleClusters.StructureCluster> unique = new LinkedHashMap<>();
        int candidateCount = 0;
        int prunedCount = 0;
        long candidateStart = System.nanoTime();
        progress.setStage(ProgressRenderer.STAGE_A_FLOW);

        for (long tileMinX = searchMinX; tileMinX <= searchMaxX; tileMinX += TILE_SIZE) {
            long tileMaxX = Math.min(searchMaxX, tileMinX + TILE_SIZE - 1);
            for (long tileMinZ = searchMinZ; tileMinZ <= searchMaxZ; tileMinZ += TILE_SIZE) {
                long tileMaxZ = Math.min(searchMaxZ, tileMinZ + TILE_SIZE - 1);

                List<BlockPoint> tileCandidates = acc.findChunks(
                        opts.seed(),
                        tileMinX - margin, tileMaxX + margin,
                        tileMinZ - margin, tileMaxZ + margin,
                        circle, centerX, centerZ, radiusSq);

                progress.setStage(ProgressRenderer.STAGE_DENSITY);
                boolean[] keep = acc.pruneByDensity(tileCandidates, opts.clusterRadius(), opts.minStructures());
                List<BlockPoint> pruned = new ArrayList<>(tileCandidates.size());
                for (int i = 0; i < tileCandidates.size(); i++) {
                    if (keep[i]) {
                        pruned.add(tileCandidates.get(i));
                    } else if (inside(tileCandidates.get(i), tileMinX, tileMaxX, tileMinZ, tileMaxZ)
                            && inSearchArea(tileCandidates.get(i), circle, centerX, centerZ, radiusSq)) {
                        prunedCount++;
                    }
                }

                // Count core candidates (owned by this tile) as the "found candidates" metric.
                for (BlockPoint p : tileCandidates) {
                    if (inside(p, tileMinX, tileMaxX, tileMinZ, tileMaxZ)
                            && inSearchArea(p, circle, centerX, centerZ, radiusSq)) {
                        candidateCount++;
                    }
                }

                List<CircleClusters.StructureCluster> clusters =
                        CircleClusters.find(pruned, opts.clusterRadius(), opts.minStructures());
                for (CircleClusters.StructureCluster cluster : clusters) {
                    BlockPoint first = cluster.structures().getFirst();
                    if (inside(first, tileMinX, tileMaxX, tileMinZ, tileMaxZ)
                            && inSearchArea(first, circle, centerX, centerZ, radiusSq)) {
                        unique.merge(cluster.structures(), cluster, SearchEngine::minimumCenter);
                    }
                }
            }
        }

        progress.setStage(ProgressRenderer.STAGE_A_FLOW);
        progress.stageDone(candidateCount);
        long tA = System.nanoTime();
        System.out.printf("[timing] A-flow enumerate + density     %.1f ms (generator-load %.1f ms)%n",
                (tA - candidateStart) / 1e6, (candidateStart - tGen) / 1e6);

        if (opts.debug()) {
            out.printf("[A flow] candidates=%d pruned=%d clusters=%d  (%.1fs)%n",
                    candidateCount, prunedCount, unique.size(),
                    (System.nanoTime() - candidateStart) / 1e9);
        }

        // ---------------------------------------------------------------- B flow (CPU parallel)
        Set<BlockPoint> required = new TreeSet<>();
        unique.values().forEach(c -> required.addAll(c.structures()));
        if (biomeChecker != null && biomeChecker.isAvailable()) {
            long before = required.size();
            required.removeIf(p -> !biomeChecker.isTrialChambersValid(opts.seed(),
                    Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16)));
            if (opts.debug()) {
                out.printf("[biome-check] %d -> %d candidates passed%n", before, required.size());
            }
        }
        progress.setStage(ProgressRenderer.STAGE_B_FLOW);
        Map<BlockPoint, List<BlockPos>> spawnersByChamber = generateAll(generator, opts, required, progress);
        int totalSpawners = spawnersByChamber.values().stream().mapToInt(List::size).sum();

        // ---------------------------------------------------------------- density scoring
        progress.setStage(ProgressRenderer.STAGE_STAT);
        List<SearchResult> results = new ArrayList<>();
        for (CircleClusters.StructureCluster cluster : unique.values()) {
            List<BlockPoint> structures = cluster.structures().stream()
                    .filter(spawnersByChamber::containsKey)
                    .sorted()
                    .toList();
            if (structures.size() < opts.minStructures()) {
                continue;
            }
            Set<SpawnerPoint> clusterSpawners = new TreeSet<>();
            structures.forEach(s -> {
                for (BlockPos p : spawnersByChamber.get(s)) {
                    clusterSpawners.add(new SpawnerPoint(p.getX(), p.getY(), p.getZ()));
                }
            });
            try {
                ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                        AreaShape.CIRCLE, opts.clusterRadius(), structures, clusterSpawners);
                if (score.spawners() >= opts.minSpawners()) {
                    results.add(new SearchResult(score.x(), score.z(), structures.size(),
                            score.spawners(), structures));
                }
            } catch (IllegalStateException ignored) {
                // no integer centre covers all structures — skip (matches original behaviour)
            }
        }
        progress.stageDone(results.size());
        progress.setStage(ProgressRenderer.STAGE_SORT);
        results.sort(java.util.Comparator.naturalOrder());

        // Truncate to 100 per structure-count group (matches the original FinderSearch output).
        List<SearchResult> limited = results.stream()
                .collect(Collectors.groupingBy(SearchResult::structureCount))
                .values().stream()
                .flatMap(group -> group.stream().sorted().limit(100))
                .sorted()
                .toList();
        progress.stageDone(limited.size());

        if (opts.debug()) {
            out.printf("[scoring] qualifying=%d truncated=%d totalSpawners=%d  (%.1fs total)%n",
                    results.size(), limited.size(), totalSpawners, (System.nanoTime() - started) / 1e9);
        }

        return new Result(candidateCount, prunedCount, unique.size(), limited.size(), totalSpawners, limited);
    }

    // ---------------------------------------------------------------- grid prefilter (single region)

    /** {@link #runGrid(Options, Accelerator, PrintStream, ProgressRenderer)} with no progress output. */
    public static Result runGrid(Options opts, Accelerator acc, PrintStream out) throws IOException {
        return runGrid(opts, acc, out, ProgressRenderer.disabled());
    }

    /**
     * Single-region grid-prefilter pipeline ({@code --prefilter-mode grid}): enumerates owned
     * candidates (A flow), GPU-aggregates their density scores into grid cells, keeps the top-K
     * cells, then B-flow generates the retained candidates and precise-clusters/scoring them.
     */
    public static Result runGrid(Options opts, Accelerator acc, PrintStream out,
                                 ProgressRenderer progress) throws IOException {
        long started = System.nanoTime();
        boolean circle = !opts.fullWorld();
        int centerX = 0;
        int centerZ = 0;
        long searchMinX = opts.fullWorld() ? -WORLD_LIMIT : Math.max(-WORLD_LIMIT, (long) centerX - opts.searchRadius());
        long searchMaxX = opts.fullWorld() ? WORLD_LIMIT : Math.min(WORLD_LIMIT, (long) centerX + opts.searchRadius());
        long searchMinZ = opts.fullWorld() ? -WORLD_LIMIT : Math.max(-WORLD_LIMIT, (long) centerZ - opts.searchRadius());
        long searchMaxZ = opts.fullWorld() ? WORLD_LIMIT : Math.min(WORLD_LIMIT, (long) centerZ + opts.searchRadius());
        long radiusSq = (long) opts.searchRadius() * opts.searchRadius();
        long margin = 2L * opts.clusterRadius() + 2;
        SimChamberGenerator generator = newGenerator(opts);

        progress.setStage(ProgressRenderer.STAGE_A_FLOW);
        // ------------------------------------------------------------------
        // Automatic tiled processing: for a huge single-region search the candidate set (each
        // 34x34-chunk region yields one chamber) can be enormous — ~1.06M per 1M-radius circle,
        // ~1.06B per 10M-radius. Instead of collecting it all, the search is automatically split
        // into tiles; each tile is enumerated + grid-prefiltered independently and only the
        // top-K-cell candidates are merged (bounded memory). The tile size is chosen adaptively so
        // every tile holds roughly the same manageable candidate count.
        long span = (long) searchMaxX - searchMinX + 1;
        long totalCandidatesEst = estimateCandidates(searchMinX, searchMaxX, searchMinZ, searchMaxZ,
                circle, radiusSq);

        // Target per-tile candidates (~5M): tile side = sqrt(5M * 295936 block^2 per chamber).
        long gridTileSize = (long) Math.sqrt(TARGET_TILE_CANDIDATES * CHAMBER_AREA_BLOCKS);
        gridTileSize = Math.max(TILE_SIZE, Math.min(MAX_GRID_TILE_SIZE, gridTileSize));

        if (totalCandidatesEst > HUGE_CANDIDATE_WARN) {
            System.out.printf("[WARN] 预估搜索范围内密室候选约 %,d 个（半径过大）。全量枚举预计需数分钟到数小时，"
                            + "已自动分片处理（每片约 %,d 候选，内存有界）。建议：减小 --search-radius，"
                            + "或改用 --full-world --top-k 做全球扫描。%n",
                    totalCandidatesEst, TARGET_TILE_CANDIDATES);
        }

        long candidateCount = estimateCandidates(searchMinX, searchMaxX, searchMinZ, searchMaxZ,
                circle, radiusSq);
        Set<BlockPoint> merged = new java.util.HashSet<>();
        int tilesProcessed = 0;
        // Overlapping tiles: a dense cluster straddling a tile boundary would otherwise be split —
        // each tile keeps its own top-K cells and the boundary-side members can drop out of both.
        // Halve-step the tile centres so every candidate is inside at least one tile by a margin
        // larger than any cluster diameter.
        long tileStep = Math.max(1, gridTileSize / 2);
        long overlap = gridTileSize - tileStep;
        int totalTilesX = (int) ((span + overlap) / tileStep);
        int totalTiles = totalTilesX * totalTilesX;
        long tiledStart = System.nanoTime();
        long lastReportNanos = 0;
        boolean gpuDirect = acc instanceof cn.trialfinder.cuda.GpuAccelerator;
        for (long tileMinX = searchMinX; tileMinX <= searchMaxX; tileMinX += tileStep) {
            long tileMaxX = Math.min(searchMaxX, tileMinX + gridTileSize - 1);
            for (long tileMinZ = searchMinZ; tileMinZ <= searchMaxZ; tileMinZ += tileStep) {
                long tileMaxZ = Math.min(searchMaxZ, tileMinZ + gridTileSize - 1);
                List<BlockPoint> tileRetained;
                if (gpuDirect) {
                    // GPU-direct grid prefilter: enumerate + count cells + select top-K entirely on
                    // the GPU, so a huge tile never constructs millions of BlockPoint objects in
                    // Java (the enumerate kernel finishes in ~2ms; host object allocation was the
                    // real A-flow bottleneck).
                    tileRetained = ((cn.trialfinder.cuda.GpuAccelerator) acc).findChunksGridPrefiltered(
                            opts.seed(),
                            tileMinX - margin, tileMaxX + margin,
                            tileMinZ - margin, tileMaxZ + margin,
                            circle, centerX, centerZ, radiusSq,
                            opts.clusterRadius(), opts.effectiveGridSize(), opts.topK());
                } else {
                    List<BlockPoint> tileCandidates = acc.findChunks(
                            opts.seed(),
                            tileMinX - margin, tileMaxX + margin,
                            tileMinZ - margin, tileMaxZ + margin,
                            circle, centerX, centerZ, radiusSq);
                    tileRetained = acc.gridAggregateAndSelect(
                            tileCandidates, opts.clusterRadius(), opts.effectiveGridSize(), opts.topK());
                }
                for (BlockPoint p : tileRetained) {
                    if (inSearchArea(p, circle, centerX, centerZ, radiusSq)) {
                        merged.add(p);
                    }
                }
                tilesProcessed++;
                // Rate-limited progress (every ~1s) with ETA, plus a debug line every 50 tiles.
                long now = System.nanoTime();
                if (!progress.isQuiet()
                        && (tilesProcessed == totalTiles
                            || opts.debug()
                            || now - lastReportNanos >= 1_000_000_000L)) {
                    double elapsed = Math.max(0.001, (now - tiledStart) / 1e9);
                    double rate = tilesProcessed / elapsed;
                    double remaining = (totalTiles - tilesProcessed) / rate;
                    String eta = ProgressRenderer.formatDurationNanos(Math.round(remaining * 1e9));
                    System.out.printf("[grid 自动分片] tile %,d/%,d (%.1f%%) 候选=%,d 保留=%,d 速率=%.0f片/s ETA=%s%n",
                            tilesProcessed, totalTiles, tilesProcessed * 100.0 / totalTiles,
                            candidateCount, merged.size(), rate, eta);
                    lastReportNanos = now;
                }
            }
        }
        progress.stageDone((int) Math.min(Integer.MAX_VALUE, candidateCount));

        progress.setStage(ProgressRenderer.STAGE_DENSITY);
        // Each tile already kept its top-K cells; the overlapping-tile union (HashSet dedup) is the
        // retained set. No second global truncation — that would drop cluster members that were
        // only present in one tile's top-K, hurting recall.
        List<BlockPoint> retained = new ArrayList<>(merged);
        int prunedCount = (int) Math.min(Integer.MAX_VALUE, candidateCount - retained.size());
        if (opts.debug()) {
            out.printf("[grid prefilter] candidates=%d retained=%d (tile-union, top %d cells/tile)%n",
                    candidateCount, retained.size(), opts.topK());
        }

        // Wrap retained candidates into a single coarse cluster; generateClusters performs the
        // B-flow generation, precise clustering and density scoring.
        List<ScoredCandidate> members = new ArrayList<>(retained.size());
        for (BlockPoint p : retained) {
            members.add(new ScoredCandidate(p, 0));
        }
        List<CoarseCluster> wrapper = members.isEmpty()
                ? List.of()
                : List.of(new CoarseCluster(members, retained.get(0)));
        List<SearchResult> results = generateClusters(wrapper, generator, opts, progress);

        progress.setStage(ProgressRenderer.STAGE_SORT);
        results.sort(java.util.Comparator.naturalOrder());
        List<SearchResult> limited = results.stream()
                .collect(Collectors.groupingBy(SearchResult::structureCount))
                .values().stream()
                .flatMap(group -> group.stream().sorted().limit(100))
                .sorted()
                .toList();
        int totalSpawners = results.stream().mapToInt(SearchResult::spawnerCount).sum();
        if (opts.debug()) {
            out.printf("[grid] qualifying=%d truncated=%d  (%.1fs total)%n",
                    results.size(), limited.size(), (System.nanoTime() - started) / 1e9);
        }
        int candidateCountInt = (int) Math.min(Integer.MAX_VALUE, candidateCount);
        return new Result(candidateCountInt, prunedCount, results.size(), limited.size(),
                totalSpawners, limited);
    }

    // ---------------------------------------------------------------- per-region search

    /** Searches one rectangular region (full-world streaming tile), region.contains ownership. */
    public static List<ResultEntry> searchRegion(SearchRegion region, Options opts, Accelerator acc)
            throws IOException {
        return searchRegion(newGenerator(opts), region, opts, acc, new RegionStats());
    }

    /**
     * Builds a generator for the given options. When {@code options.cache()} is non-null (i.e.
     * the CLI was invoked with {@code --cache}), the generator is wired to that {@link SpawnerCache};
     * otherwise no cache is attached and B-flow generation performs no I/O.
     */
    private static SimChamberGenerator newGenerator(Options opts) {
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        if (opts.cache() != null) {
            generator.setCache(opts.cache());
        }
        if (opts.jigsawDepth() > 0) {
            generator.setJigsawDepth(opts.jigsawDepth());
        }
        return generator;
    }

    /**
     * Searches one rectangular region with {@link WorldTiler} ownership (overlapping full-world
     * tiles): each cluster is scored by exactly one tile (the one owning its first member).
     */
    public static List<ResultEntry> searchRegion(
            SimChamberGenerator generator, SearchRegion region, WorldTiler tiler,
            Options opts, Accelerator acc, RegionStats stats) throws IOException {
        return searchRegion(generator, region, tiler, opts, acc, stats, ProgressRenderer.disabled());
    }

    /** {@link #searchRegion(SimChamberGenerator, SearchRegion, WorldTiler, Options, Accelerator, RegionStats)} with progress reporting. */
    public static List<ResultEntry> searchRegion(
            SimChamberGenerator generator, SearchRegion region, WorldTiler tiler,
            Options opts, Accelerator acc, RegionStats stats, ProgressRenderer progress) throws IOException {
        return searchRegion(generator, region, tiler, opts, acc, stats, progress,
                (r, first) -> tiler.owns(r, first.x(), first.z()));
    }

    /**
     * Searches one rectangular region. Enumerates candidates in {@code region} expanded by an
     * overlap margin (so clusters straddling a tile boundary are seen in full), keeps clusters
     * whose first (sorted) member is owned by this region, runs B-flow generation for those
     * clusters' chambers and scores them.
     *
     * <p>Ownership is provided by {@code owned} (default {@code region.contains}); the margin is
     * {@code max(tileOverlap, 2*clusterRadius+2)} blocks so any cluster with an owned member is
     * fully enumerated and scored by exactly one tile.
     *
     * @return qualifying results, sorted by {@link ResultEntry#compareTo}
     */
    public static List<ResultEntry> searchRegion(
            SimChamberGenerator generator, SearchRegion region, Options opts, Accelerator acc,
            RegionStats stats) throws IOException {
        return searchRegion(generator, region, null, opts, acc, stats, ProgressRenderer.disabled(),
                (r, first) -> r.contains(first.x(), first.z()));
    }

    private static List<ResultEntry> searchRegion(
            SimChamberGenerator generator, SearchRegion region, WorldTiler tiler,
            Options opts, Accelerator acc, RegionStats stats, ProgressRenderer progress,
            java.util.function.BiPredicate<SearchRegion, BlockPoint> owned) throws IOException {

        long margin = Math.max((long) opts.tileOverlap(), 2L * opts.clusterRadius() + 2);
        progress.setStage(ProgressRenderer.STAGE_A_FLOW);
        List<BlockPoint> candidates = acc.findChunks(
                opts.seed(),
                region.minX() - margin, region.maxX() + margin,
                region.minZ() - margin, region.maxZ() + margin,
                false, 0, 0, 0);

        progress.setStage(ProgressRenderer.STAGE_DENSITY);
        boolean[] keep = acc.pruneByDensity(candidates, opts.clusterRadius(), opts.minStructures());
        List<BlockPoint> pruned = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            if (keep[i]) {
                pruned.add(candidates.get(i));
            }
        }

        for (BlockPoint p : candidates) {
            if (region.contains(p.x(), p.z())) {
                stats.candidateCount++;
            }
        }

        List<CircleClusters.StructureCluster> ownedClusters = new ArrayList<>();
        for (CircleClusters.StructureCluster cluster :
                CircleClusters.find(pruned, opts.clusterRadius(), opts.minStructures())) {
            BlockPoint first = cluster.structures().getFirst();
            if (owned.test(region, first)) {
                ownedClusters.add(cluster);
            }
        }

        Set<BlockPoint> required = new TreeSet<>();
        ownedClusters.forEach(cluster -> required.addAll(cluster.structures()));
        progress.setStage(ProgressRenderer.STAGE_B_FLOW);
        Map<BlockPoint, List<BlockPos>> spawnersByChamber = generateAll(generator, opts, required, progress);
        stats.chamberCount += required.size();
        stats.spawnerCount += spawnersByChamber.values().stream().mapToInt(List::size).sum();

        progress.setStage(ProgressRenderer.STAGE_STAT);
        List<ResultEntry> results = new ArrayList<>();
        for (CircleClusters.StructureCluster cluster : ownedClusters) {
            List<BlockPoint> structures = cluster.structures().stream()
                    .filter(spawnersByChamber::containsKey)
                    .sorted()
                    .toList();
            if (structures.size() < opts.minStructures()) {
                continue;
            }
            Set<SpawnerPoint> clusterSpawners = new TreeSet<>();
            structures.forEach(s -> {
                for (BlockPos p : spawnersByChamber.get(s)) {
                    clusterSpawners.add(new SpawnerPoint(p.getX(), p.getY(), p.getZ()));
                }
            });
            try {
                ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                        AreaShape.CIRCLE, opts.clusterRadius(), structures, clusterSpawners);
                if (score.spawners() >= opts.minSpawners()) {
                    results.add(ResultEntry.from(new SearchResult(
                            score.x(), score.z(), structures.size(), score.spawners(), structures)));
                }
            } catch (IllegalStateException ignored) {
                // no integer centre covers all structures — skip
            }
        }
        progress.setStage(ProgressRenderer.STAGE_SORT);
        results.sort(ResultEntry::compareTo);
        return results;
    }

    // ---------------------------------------------------------------- grid prefilter (per-region / full-world tile)

    /** Per-tile grid-prefilter search ({@code --prefilter-mode grid}), mirroring {@link #searchRegion}. */
    public static List<ResultEntry> searchRegionGrid(
            SimChamberGenerator generator, SearchRegion region, WorldTiler tiler,
            Options opts, Accelerator acc, RegionStats stats, ProgressRenderer progress)
            throws IOException {
        return searchRegionGrid(generator, region, tiler, opts, acc, stats, progress,
                (r, first) -> tiler.owns(r, first.x(), first.z()));
    }

    private static List<ResultEntry> searchRegionGrid(
            SimChamberGenerator generator, SearchRegion region, WorldTiler tiler,
            Options opts, Accelerator acc, RegionStats stats, ProgressRenderer progress,
            java.util.function.BiPredicate<SearchRegion, BlockPoint> owned) throws IOException {

        long margin = Math.max((long) opts.tileOverlap(), 2L * opts.clusterRadius() + 2);
        progress.setStage(ProgressRenderer.STAGE_A_FLOW);
        List<BlockPoint> candidates = acc.findChunks(
                opts.seed(),
                region.minX() - margin, region.maxX() + margin,
                region.minZ() - margin, region.maxZ() + margin,
                false, 0, 0, 0);

        List<BlockPoint> ownedCandidates = new ArrayList<>();
        for (BlockPoint p : candidates) {
            if (owned.test(region, p)) {
                ownedCandidates.add(p);
            }
        }
        stats.candidateCount += ownedCandidates.size();

        progress.setStage(ProgressRenderer.STAGE_DENSITY);
        List<BlockPoint> retained = acc.gridAggregateAndSelect(
                ownedCandidates, opts.clusterRadius(), opts.effectiveGridSize(), opts.topK());

        progress.setStage(ProgressRenderer.STAGE_B_FLOW);
        Set<BlockPoint> required = new TreeSet<>(retained);
        Map<BlockPoint, List<BlockPos>> spawnersByChamber = generateAll(generator, opts, required, progress);
        stats.chamberCount += required.size();
        stats.spawnerCount += spawnersByChamber.values().stream().mapToInt(List::size).sum();

        progress.setStage(ProgressRenderer.STAGE_STAT);
        List<CircleClusters.StructureCluster> precise = CircleClusters.find(
                new ArrayList<>(required), opts.clusterRadius(), opts.minStructures());
        List<ResultEntry> results = new ArrayList<>();
        for (CircleClusters.StructureCluster cluster : precise) {
            List<BlockPoint> structures = cluster.structures().stream()
                    .filter(spawnersByChamber::containsKey)
                    .sorted()
                    .toList();
            if (structures.size() < opts.minStructures()) {
                continue;
            }
            Set<SpawnerPoint> clusterSpawners = new TreeSet<>();
            structures.forEach(s -> {
                for (BlockPos p : spawnersByChamber.get(s)) {
                    clusterSpawners.add(new SpawnerPoint(p.getX(), p.getY(), p.getZ()));
                }
            });
            try {
                ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                        AreaShape.CIRCLE, opts.clusterRadius(), structures, clusterSpawners);
                if (score.spawners() >= opts.minSpawners()) {
                    results.add(ResultEntry.from(new SearchResult(
                            score.x(), score.z(), structures.size(), score.spawners(), structures)));
                }
            } catch (IllegalStateException ignored) {
                // no integer centre covers all structures — skip
            }
        }
        progress.setStage(ProgressRenderer.STAGE_SORT);
        results.sort(ResultEntry::compareTo);
        return results;
    }

    /** Writes a tile's results to a sorted temp file (one ResultEntry per line, no header). */
    public static void writeTileTempFile(Path tempFile, List<ResultEntry> entries) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, java.nio.charset.StandardCharsets.UTF_8)) {
            for (ResultEntry entry : entries) {
                writer.write(entry.toCsvLine());
                writer.newLine();
            }
        }
    }

    // ---------------------------------------------------------------- top-K density pipeline

    /** A candidate chamber plus its coarse density score (2R neighbour count). */
    public record ScoredCandidate(BlockPoint point, int score) {
    }

    /** Best-first order for top-K output: score desc, then X asc, then Z asc. */
    public static final Comparator<ScoredCandidate> TOP_K_BEST_FIRST = Comparator
            .comparingInt(ScoredCandidate::score).reversed()
            .thenComparing(c -> c.point().x())
            .thenComparing(c -> c.point().z());

    /**
     * Worst-first order for the bounded min-heap used to accumulate the global top-K: poll()
     * removes the lowest score first, and among equal scores the largest (X,Z) — so the survivors
     * are exactly the K candidates that come first under {@link #TOP_K_BEST_FIRST}.
     */
    public static final Comparator<ScoredCandidate> TOP_K_WORST_FIRST = Comparator
            .comparingInt(ScoredCandidate::score)
            .thenComparing(Comparator.comparingInt((ScoredCandidate c) -> c.point().x()).reversed())
            .thenComparing(Comparator.comparingInt((ScoredCandidate c) -> c.point().z()).reversed());

    /**
     * Scores every candidate owned by {@code region}: enumerates the region plus an overlap margin
     * (so a candidate sees all of its 2R neighbours across tile boundaries), computes the 2R
     * neighbour count via the accelerator, and keeps only the candidates owned by this region
     * (deterministic, no duplicates across tiles). No B-flow is performed.
     *
     * @param circle when true, candidates are additionally restricted to the circle centred at
     *               {@code (centerX,centerZ)} of squared radius {@code radiusSq} (single-region use)
     */
    public static List<ScoredCandidate> scoreRegion(
            SearchRegion region, WorldTiler tiler, Options opts, Accelerator acc,
            boolean circle, int centerX, int centerZ, long radiusSq) {
        return scoreRegion(region, tiler, opts, acc, circle, centerX, centerZ, radiusSq,
                ProgressRenderer.disabled());
    }

    /** {@link #scoreRegion(SearchRegion, WorldTiler, Options, Accelerator, boolean, int, int, long)} with progress reporting. */
    public static List<ScoredCandidate> scoreRegion(
            SearchRegion region, WorldTiler tiler, Options opts, Accelerator acc,
            boolean circle, int centerX, int centerZ, long radiusSq, ProgressRenderer progress) {
        List<ScoredCandidate> all = scoreAllCandidates(
                region, tiler, opts, acc, circle, centerX, centerZ, radiusSq, progress);
        List<ScoredCandidate> out = new ArrayList<>();
        for (ScoredCandidate candidate : all) {
            BlockPoint p = candidate.point();
            boolean owned = tiler != null
                    ? tiler.owns(region, p.x(), p.z())
                    : region.contains(p.x(), p.z());
            if (owned) {
                out.add(candidate);
            }
        }
        return out;
    }

    /**
     * Scores every candidate in {@code region} expanded by an overlap margin (so a candidate sees
     * all of its 2R neighbours across tile boundaries). Returns ALL scored candidates — the caller
     * applies ownership. Used by the cluster-level top-K pipeline so coarse clusters form complete.
     */
    public static List<ScoredCandidate> scoreAllCandidates(
            SearchRegion region, WorldTiler tiler, Options opts, Accelerator acc,
            boolean circle, int centerX, int centerZ, long radiusSq) {
        return scoreAllCandidates(region, tiler, opts, acc, circle, centerX, centerZ, radiusSq,
                ProgressRenderer.disabled());
    }

    /** {@link #scoreAllCandidates(SearchRegion, WorldTiler, Options, Accelerator, boolean, int, int, long)} with progress reporting. */
    public static List<ScoredCandidate> scoreAllCandidates(
            SearchRegion region, WorldTiler tiler, Options opts, Accelerator acc,
            boolean circle, int centerX, int centerZ, long radiusSq, ProgressRenderer progress) {
        long margin = Math.max((long) opts.tileOverlap(), 2L * opts.clusterRadius() + 2);
        progress.setStage(ProgressRenderer.STAGE_A_FLOW);
        List<BlockPoint> expanded = acc.findChunks(
                opts.seed(),
                region.minX() - margin, region.maxX() + margin,
                region.minZ() - margin, region.maxZ() + margin,
                circle, centerX, centerZ, radiusSq);
        progress.setStage(ProgressRenderer.STAGE_DENSITY);
        int[] scores = acc.densityScores(expanded, opts.clusterRadius());

        List<ScoredCandidate> out = new ArrayList<>(expanded.size());
        for (int i = 0; i < expanded.size(); i++) {
            out.add(new ScoredCandidate(expanded.get(i), scores[i]));
        }
        return out;
    }

    // ---------------------------------------------------------------- cluster-level top-K

    /**
     * A coarse cluster: a connected component of scored candidates linked by distance
     * &le; {@code 2*clusterRadius}. Each member belongs to exactly one coarse cluster, so a final
     * precise cluster (diameter &le; 2R) is never split across coarse clusters.
     */
    public record CoarseCluster(List<ScoredCandidate> members, BlockPoint representative) {
        public CoarseCluster {
            members = List.copyOf(members);
        }

        public int size() {
            return this.members.size();
        }
    }

    /** Best-first order for coarse-cluster output: size desc, then X asc, then Z asc. */
    public static final Comparator<CoarseCluster> COARSE_BEST_FIRST = Comparator
            .comparingInt(CoarseCluster::size).reversed()
            .thenComparing(c -> c.representative().x())
            .thenComparing(c -> c.representative().z());

    /**
     * Worst-first order for the bounded min-heap used to accumulate the global top-K coarse
     * clusters: poll() removes the smallest cluster first, and among equal sizes the largest
     * (X,Z) — so the survivors are exactly the K clusters that come first under
     * {@link #COARSE_BEST_FIRST}.
     */
    public static final Comparator<CoarseCluster> COARSE_WORST_FIRST = Comparator
            .comparingInt(CoarseCluster::size)
            .thenComparing(Comparator.comparingInt((CoarseCluster c) -> c.representative().x()).reversed())
            .thenComparing(Comparator.comparingInt((CoarseCluster c) -> c.representative().z()).reversed());

    /**
     * Retains the top {@code k} coarse clusters under {@link #COARSE_BEST_FIRST} using a bounded
     * min-heap (O(n log k)) instead of sorting all {@code n} clusters (O(n log n)). Returns the
     * survivors sorted best-first, or the whole input sorted when {@code k <= 0} or the input is
     * no larger than {@code k}.
     */
    public static List<CoarseCluster> retainTopK(List<CoarseCluster> clusters, int k) {
        if (k <= 0 || clusters.size() <= k) {
            List<CoarseCluster> sorted = new ArrayList<>(clusters);
            sorted.sort(COARSE_BEST_FIRST);
            return sorted;
        }
        PriorityQueue<CoarseCluster> heap = new PriorityQueue<>(k + 1, COARSE_WORST_FIRST);
        for (CoarseCluster cluster : clusters) {
            heap.add(cluster);
            if (heap.size() > k) {
                heap.poll();
            }
        }
        List<CoarseCluster> retained = new ArrayList<>(heap);
        retained.sort(COARSE_BEST_FIRST);
        return retained;
    }

    /**
     * Coarse-clusters every scored candidate in {@code region}+margin (so clusters spanning a tile
     * boundary form complete), then keeps only clusters whose representative is owned by this
     * region (deterministic; no duplicates across tiles) and with at least {@code minStructures}
     * members. No B-flow is performed.
     */
    public static List<CoarseCluster> coarseClustersForRegion(
            SearchRegion region, WorldTiler tiler, Options opts, Accelerator acc,
            boolean circle, int centerX, int centerZ, long radiusSq) {
        return coarseClustersForRegion(region, tiler, opts, acc, circle, centerX, centerZ, radiusSq,
                ProgressRenderer.disabled());
    }

    /** {@link #coarseClustersForRegion(SearchRegion, WorldTiler, Options, Accelerator, boolean, int, int, long)} with progress reporting. */
    public static List<CoarseCluster> coarseClustersForRegion(
            SearchRegion region, WorldTiler tiler, Options opts, Accelerator acc,
            boolean circle, int centerX, int centerZ, long radiusSq, ProgressRenderer progress) {
        long t0 = System.nanoTime();
        List<ScoredCandidate> all = scoreAllCandidates(
                region, tiler, opts, acc, circle, centerX, centerZ, radiusSq, progress);

        // Lossless sparse-tile prefilter: a candidate with density score < minStructures cannot be
        // a member of a qualifying cluster (its 2R neighbourhood has fewer than minStructures
        // points), so drop it before the (potentially expensive) coarse clustering. This does not
        // change the final cluster set — a qualifying precise cluster's members all survive.
        List<ScoredCandidate> dense = new ArrayList<>(all.size());
        for (ScoredCandidate candidate : all) {
            if (candidate.score() >= opts.minStructures()) {
                dense.add(candidate);
            }
        }

        if (opts.debug()) {
            System.out.printf("[DEBUG] coarse candidates: %d -> %d (score>=%d) | 取 %s%n",
                    all.size(), dense.size(), opts.minStructures(), region);
        }

        // Skip coarse clustering entirely for sparse tiles below the threshold.
        if (dense.size() < opts.effectiveMinCandidatesPerTile()) {
            if (opts.debug()) {
                System.out.printf("[DEBUG] skip sparse tile (dense=%d < threshold=%d) after %.1f ms%n",
                        dense.size(), opts.effectiveMinCandidatesPerTile(),
                        (System.nanoTime() - t0) / 1e6);
            }
            return List.of();
        }

        List<CoarseCluster> clusters;
        if ("legacy".equalsIgnoreCase(opts.clusterMethod())) {
            clusters = coarseClusterAll(dense, 2 * opts.clusterRadius());
        } else {
            clusters = densityPeakCluster(dense, 2 * opts.clusterRadius(),
                    opts.effectiveMaxClusterSize(dense.size()));
        }

        List<CoarseCluster> owned = new ArrayList<>();
        for (CoarseCluster cluster : clusters) {
            BlockPoint rep = cluster.representative();
            boolean isOwned = tiler != null
                    ? tiler.owns(region, rep.x(), rep.z())
                    : region.contains(rep.x(), rep.z());
            if (isOwned && cluster.size() >= opts.minStructures()) {
                owned.add(cluster);
            }
        }

        if (opts.debug()) {
            System.out.printf("[DEBUG] coarse clusters: %d -> owned %d | 聚类 %.1f ms%n",
                    clusters.size(), owned.size(), (System.nanoTime() - t0) / 1e6);
        }
        return owned;
    }

    /** Union-find coarse clustering over scored candidates with link distance {@code linkDistance}. */
    static List<CoarseCluster> coarseClusterAll(List<ScoredCandidate> candidates, int linkDistance) {
        int n = candidates.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        int cell = Math.max(1, linkDistance);
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < n; i++) {
            BlockPoint p = candidates.get(i).point();
            grid.computeIfAbsent(cellKey(Math.floorDiv(p.x(), cell), Math.floorDiv(p.z(), cell)),
                    ignored -> new ArrayList<>()).add(i);
        }
        long distSq = (long) linkDistance * linkDistance;
        for (int i = 0; i < n; i++) {
            BlockPoint p = candidates.get(i).point();
            int cx = Math.floorDiv(p.x(), cell);
            int cz = Math.floorDiv(p.z(), cell);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int j : grid.getOrDefault(cellKey(cx + dx, cz + dz), List.of())) {
                        if (j >= i) {
                            continue; // process each pair once (i<j)
                        }
                        BlockPoint q = candidates.get(j).point();
                        long ox = (long) p.x() - q.x();
                        long oz = (long) p.z() - q.z();
                        if (ox * ox + oz * oz <= distSq) {
                            union(parent, i, j);
                        }
                    }
                }
            }
        }
        Map<Integer, List<ScoredCandidate>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), ignored -> new ArrayList<>()).add(candidates.get(i));
        }
        List<CoarseCluster> result = new ArrayList<>(groups.size());
        for (List<ScoredCandidate> members : groups.values()) {
            members.sort(Comparator.comparing((ScoredCandidate c) -> c.point().x())
                    .thenComparing(c -> c.point().z()));
            result.add(new CoarseCluster(members, members.get(0).point()));
        }
        return result;
    }

    // ---------------------------------------------------------------- density-aware coarse clustering

    /**
     * Density-peak coarse clustering (deterministic, no randomness).
     *
     * <p>Each candidate's density is its 2R-neighbour count (its {@link ScoredCandidate#score()}).
     * A candidate points to the nearest candidate with a <i>strictly better</i> density score
     * (tie-break by smaller X then Z, so the "better" relation is a total order → pointers are
     * acyclic) within {@code coarseRadius}. Candidates with no such neighbour are density peaks;
     * the basins of attraction of each peak form one coarse cluster.
     *
     * <p>Clusters larger than {@code maxClusterSize} are recursively re-clustered with a halved
     * radius, separating sub-peaks while keeping high-density cores intact.
     */
    public static List<CoarseCluster> densityPeakCluster(
            List<ScoredCandidate> candidates, int coarseRadius, int maxClusterSize) {
        List<CoarseCluster> clusters = densityPeakClusterOnce(candidates, coarseRadius);
        List<CoarseCluster> result = new ArrayList<>(clusters.size());
        for (CoarseCluster cluster : clusters) {
            if (cluster.size() > maxClusterSize && coarseRadius > 1) {
                result.addAll(densityPeakCluster(cluster.members(),
                        Math.max(1, coarseRadius / 2), maxClusterSize));
            } else {
                result.add(cluster);
            }
        }
        return result;
    }

    /** One density-peak pass over the candidate set at a fixed radius. */
    static List<CoarseCluster> densityPeakClusterOnce(List<ScoredCandidate> candidates, int coarseRadius) {
        int n = candidates.size();
        if (n == 0) {
            return List.of();
        }
        // parent[i] = nearest strictly-better candidate within coarseRadius, or -1 if it's a peak.
        // Uses the KD-tree spatial index for the range query (O(n log n) expected).
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        SpatialIndex index = new SpatialIndex(candidates);
        for (int i = 0; i < n; i++) {
            parent[i] = index.nearestBetter(i, coarseRadius);
        }

        // Basins: follow pointer chains to peaks, path-compressed.
        int[] root = new int[n];
        Arrays.fill(root, -1);
        Map<Integer, List<ScoredCandidate>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int r = findPeak(parent, root, i);
            groups.computeIfAbsent(r, ignored -> new ArrayList<>()).add(candidates.get(i));
        }
        List<CoarseCluster> result = new ArrayList<>(groups.size());
        for (List<ScoredCandidate> members : groups.values()) {
            members.sort(Comparator.comparing((ScoredCandidate c) -> c.point().x())
                    .thenComparing(c -> c.point().z()));
            result.add(new CoarseCluster(members, members.get(0).point()));
        }
        return result;
    }

    /** Strict total order: higher score is "better"; ties broken by smaller X then Z. */
    private static boolean better(List<ScoredCandidate> candidates, int a, int b) {
        int sa = candidates.get(a).score();
        int sb = candidates.get(b).score();
        if (sa != sb) {
            return sa > sb;
        }
        BlockPoint pa = candidates.get(a).point();
        BlockPoint pb = candidates.get(b).point();
        if (pa.x() != pb.x()) {
            return pa.x() < pb.x();
        }
        return pa.z() < pb.z();
    }

    private static int findPeak(int[] parent, int[] root, int i) {
        int r = i;
        while (parent[r] != -1) {
            r = parent[r];
        }
        // path compression
        int cur = i;
        while (cur != r) {
            int next = parent[cur];
            parent[cur] = r;
            cur = next;
        }
        root[i] = r;
        return r;
    }

    // ---------------------------------------------------------------- KD-tree spatial index

    /**
     * Lightweight KD-tree over the scored candidates, used by {@link #densityPeakClusterOnce}
     * (via the grid) or independently. Supports "nearest candidate strictly better than {@code i}
     * within a max distance" queries with O(log n) expected pruning. Deterministic: ties are broken
     * by the candidate index.
     */
    static final class SpatialIndex {
        private final List<ScoredCandidate> candidates;
        private final Node root;

        SpatialIndex(List<ScoredCandidate> candidates) {
            this.candidates = candidates;
            int n = candidates.size();
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }
            this.root = build(order, 0, n, 0);
        }

        private Node build(Integer[] order, int from, int to, int depth) {
            if (from >= to) {
                return null;
            }
            int axis = depth % 2; // 0 = X, 1 = Z
            // Sort the slice by axis to find the median (deterministic; index tie-break).
            sortByAxis(order, from, to, axis);
            int mid = (from + to) >>> 1;
            int index = order[mid];
            BlockPoint p = this.candidates.get(index).point();
            boolean axisIsX = axis == 0;
            Node node = new Node(index, axisIsX, axisIsX ? p.x() : p.z());
            node.left = build(order, from, mid, depth + 1);
            node.right = build(order, mid + 1, to, depth + 1);
            return node;
        }

        private void sortByAxis(Integer[] order, int from, int to, int axis) {
            Arrays.sort(order, from, to, (a, b) -> {
                BlockPoint pa = this.candidates.get(a).point();
                BlockPoint pb = this.candidates.get(b).point();
                int va = axis == 0 ? pa.x() : pa.z();
                int vb = axis == 0 ? pb.x() : pb.z();
                if (va != vb) {
                    return Integer.compare(va, vb);
                }
                return Integer.compare(a, b);
            });
        }

        /**
         * Returns the index of the nearest candidate strictly better than {@code target} within
         * {@code maxDistance} (Euclidean), or -1 if none. Tie-break by candidate index.
         * Stateless per call (thread-safe on an immutable index).
         */
        int nearestBetter(int target, int maxDistance) {
            BlockPoint p = this.candidates.get(target).point();
            long radiusSq = (long) maxDistance * maxDistance;
            QueryState state = new QueryState();
            search(this.root, p, target, radiusSq, state);
            return state.bestIndex;
        }

        private static final class QueryState {
            int bestIndex = -1;
            long bestDistSq = Long.MAX_VALUE;
        }

        private void search(Node node, BlockPoint p, int target, long radiusSq, QueryState state) {
            if (node == null) {
                return;
            }
            long axisDelta;
            if (node.axisIsX) {
                axisDelta = p.x() - node.value;
            } else {
                axisDelta = p.z() - node.value;
            }
            Node near = axisDelta < 0 ? node.left : node.right;
            Node far = axisDelta < 0 ? node.right : node.left;
            search(near, p, target, radiusSq, state);
            long d2 = axisDelta * axisDelta;
            if (state.bestIndex == -1 || d2 <= state.bestDistSq) {
                if (withinRadiusAndBetter(node.index, p, target, radiusSq)) {
                    long nodeDist = distSq(node.index, p);
                    if (state.bestIndex == -1 || nodeDist < state.bestDistSq
                            || (nodeDist == state.bestDistSq && node.index < state.bestIndex)) {
                        state.bestIndex = node.index;
                        state.bestDistSq = nodeDist;
                    }
                }
                search(far, p, target, radiusSq, state);
            }
        }

        private boolean withinRadiusAndBetter(int j, BlockPoint p, int target, long radiusSq) {
            if (j == target) {
                return false;
            }
            long d2 = distSq(j, p);
            if (d2 > radiusSq) {
                return false;
            }
            return better(this.candidates, j, target);
        }

        private long distSq(int j, BlockPoint p) {
            BlockPoint q = this.candidates.get(j).point();
            long ox = (long) p.x() - q.x();
            long oz = (long) p.z() - q.z();
            return ox * ox + oz * oz;
        }

        private static final class Node {
            final int index;
            final int value;       // x or z depending on axis
            final boolean axisIsX; // true = X axis, false = Z axis
            Node left;
            Node right;

            Node(int index, boolean axisIsX, int value) {
                this.index = index;
                this.axisIsX = axisIsX;
                this.value = value;
            }
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffff_ffffL);
    }

    /**
     * B-flow phase of the cluster-level top-K pipeline: generates every chamber of the retained
     * coarse clusters in parallel, precise-clusters the retained chambers with the exact
     * {@link CircleClusters}, and scores each precise cluster with {@link ExactCenterOptimizer}.
     * Returns complete clusters (all member chambers), sorted by real spawner count descending.
     */
    public static List<SearchResult> generateClusters(
            List<CoarseCluster> clusters, SimChamberGenerator generator, Options opts)
            throws IOException {
        return generateClusters(clusters, generator, opts, ProgressRenderer.disabled());
    }

    /** {@link #generateClusters(List, SimChamberGenerator, Options)} with parallel progress reporting. */
    public static List<SearchResult> generateClusters(
            List<CoarseCluster> clusters, SimChamberGenerator generator, Options opts,
            ProgressRenderer progress) throws IOException {
        Set<BlockPoint> required = new TreeSet<>();
        for (CoarseCluster cluster : clusters) {
            for (ScoredCandidate member : cluster.members()) {
                required.add(member.point());
            }
        }
        progress.setStage(ProgressRenderer.STAGE_B_FLOW);
        Map<BlockPoint, List<BlockPos>> spawnersByChamber = generateAll(generator, opts, required, progress);

        progress.setStage(ProgressRenderer.STAGE_STAT);
        List<CircleClusters.StructureCluster> precise = CircleClusters.find(
                new ArrayList<>(required), opts.clusterRadius(), opts.minStructures());

        List<SearchResult> results = new ArrayList<>();
        for (CircleClusters.StructureCluster cluster : precise) {
            List<BlockPoint> structures = cluster.structures().stream()
                    .filter(spawnersByChamber::containsKey)
                    .sorted()
                    .toList();
            if (structures.size() < opts.minStructures()) {
                continue;
            }
            Set<SpawnerPoint> clusterSpawners = new TreeSet<>();
            for (BlockPoint s : structures) {
                for (BlockPos p : spawnersByChamber.get(s)) {
                    clusterSpawners.add(new SpawnerPoint(p.getX(), p.getY(), p.getZ()));
                }
            }
            try {
                ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                        AreaShape.CIRCLE, opts.clusterRadius(), structures, clusterSpawners);
                if (score.spawners() >= opts.minSpawners()) {
                    results.add(new SearchResult(score.x(), score.z(), structures.size(),
                            score.spawners(), structures));
                }
            } catch (IllegalStateException ignored) {
                // no integer centre covers all structures — skip
            }
        }
        progress.setStage(ProgressRenderer.STAGE_SORT);
        results.sort(java.util.Comparator.naturalOrder());
        return results;
    }

    /**
     * B-flow phase of the top-K pipeline: generates the retained candidate chambers in parallel,
     * counts real spawners, and returns results with spawner count &gt;= {@code minSpawners}, sorted
     * by real spawner count descending (then structure count, X, Z via {@link SearchResult#compareTo}).
     */
    public static List<SearchResult> generateChambers(
            SimChamberGenerator generator, Options opts, List<ScoredCandidate> retained)
            throws IOException {
        return generateChambers(generator, opts, retained, ProgressRenderer.disabled());
    }

    /** {@link #generateChambers(SimChamberGenerator, Options, List)} with parallel progress reporting. */
    public static List<SearchResult> generateChambers(
            SimChamberGenerator generator, Options opts, List<ScoredCandidate> retained,
            ProgressRenderer progress) throws IOException {
        int threads = Math.max(1, opts.threads());
        progress.setStage(ProgressRenderer.STAGE_B_FLOW);
        ConcurrentHashMap<BlockPoint, Integer> spawnerCounts = new ConcurrentHashMap<>();
        long startNanos = System.nanoTime();
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        int total = retained.size();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (ScoredCandidate candidate : retained) {
                futures.add(executor.submit(() -> {
                    BlockPoint point = candidate.point();
                    int chunkX = Math.floorDiv(point.x(), 16);
                    int chunkZ = Math.floorDiv(point.z(), 16);
                    SimChamberGenerator.ChamberResult result =
                            generator.generateChamber(opts.seed(), chunkX, chunkZ).orElse(null);
                    if (result != null) {
                        spawnerCounts.put(point, result.spawnerPositions().size());
                    }
                    reportGenerationProgress(progress, done.incrementAndGet(), total, startNanos);
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException("chamber generation failed", e);
                }
            }
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (ScoredCandidate candidate : retained) {
            Integer spawnerCount = spawnerCounts.get(candidate.point());
            if (spawnerCount != null && spawnerCount >= opts.minSpawners()) {
                BlockPoint p = candidate.point();
                results.add(new SearchResult(p.x(), p.z(), 1, spawnerCount, List.of(p)));
            }
        }
        results.sort(java.util.Comparator.naturalOrder());
        return results;
    }

    private static boolean inside(BlockPoint p, long minX, long maxX, long minZ, long maxZ) {
        return p.x() >= minX && p.x() <= maxX && p.z() >= minZ && p.z() <= maxZ;
    }

    private static boolean inSearchArea(BlockPoint p, boolean circle, int centerX, int centerZ, long radiusSq) {
        if (!circle) {
            return true;
        }
        long dx = (long) p.x() - centerX;
        long dz = (long) p.z() - centerZ;
        return dx * dx + dz * dz <= radiusSq;
    }

    private static CircleClusters.StructureCluster minimumCenter(
            CircleClusters.StructureCluster a, CircleClusters.StructureCluster b) {
        int byX = Long.compare(a.center().roundedX(), b.center().roundedX());
        if (byX != 0) {
            return byX < 0 ? a : b;
        }
        return a.center().roundedZ() <= b.center().roundedZ() ? a : b;
    }

    private static Map<BlockPoint, List<BlockPos>> generateAll(
            SimChamberGenerator generator, Options opts, Set<BlockPoint> candidates,
            ProgressRenderer progress) {
        int threads = Math.max(1, opts.threads());
        ConcurrentHashMap<BlockPoint, List<BlockPos>> spawners = new ConcurrentHashMap<>();
        long startNanos = System.nanoTime();
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        int total = candidates.size();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (BlockPoint candidate : candidates) {
                futures.add(executor.submit(() -> {
                    int chunkX = Math.floorDiv(candidate.x(), 16);
                    int chunkZ = Math.floorDiv(candidate.z(), 16);
                    SimChamberGenerator.ChamberResult result =
                            generator.generateChamber(opts.seed(), chunkX, chunkZ).orElse(null);
                    if (result != null && !result.spawnerPositions().isEmpty()) {
                        spawners.put(candidate, result.spawnerPositions());
                    }
                    reportGenerationProgress(progress, done.incrementAndGet(), total, startNanos);
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException("chamber generation failed", e);
                }
            }
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return spawners;
    }

    /** Thread-safe, rate-limited progress report for parallel B-flow generation. */
    private static void reportGenerationProgress(ProgressRenderer progress, int current, int total,
                                                 long startNanos) {
        if (progress == null || progress.isQuiet()) {
            return;
        }
        double elapsed = Math.max(0.001, (System.nanoTime() - startNanos) / 1_000_000_000.0);
        double rate = current / elapsed;
        long remainingNanos = current == 0 ? 0
                : Math.max(0, Math.round((double) elapsed * (total - current) / current * 1_000_000_000.0));
        progress.update(current, total, rate,
                ProgressRenderer.formatDurationNanos(remainingNanos));
    }

    /** Writes results via the standard CSV + aligned-text writer. {@code csvPath} must end in .csv. */
    public static Path writeResults(Path csvPath, List<SearchResult> results) throws IOException {
        cn.trialfinder.io.ResultWriter.write(csvPath, results);
        return csvPath;
    }
}
