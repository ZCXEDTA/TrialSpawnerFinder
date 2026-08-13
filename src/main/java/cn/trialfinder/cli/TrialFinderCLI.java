package cn.trialfinder.cli;

import cn.trialfinder.accel.Accelerator;
import cn.trialfinder.accel.CpuAccelerator;
import cn.trialfinder.cuda.GpuAccelerator;
import cn.trialfinder.sim.SimChamberGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Command-line entry point for the standalone Trial Chambers finder.
 *
 * <p>A-flow candidate enumeration and the density pre-filter are CUDA-accelerated when a GPU is
 * available; jigsaw assembly (B flow) always runs on the CPU thread pool. The CLI degrades to a
 * fully CPU path automatically if CUDA/JCuda cannot be initialised.
 *
 * <p>{@code --full-world} scans the entire ±30,000,000 block world by streaming over
 * {@code --tile-size} tiles: each tile is searched independently (A flow GPU → density pre-filter
 * GPU → B flow CPU → density scoring), its results are written to a temp file, and the temp files
 * are N-way merged at the end.
 *
 * <p>When a {@code finder.properties} file exists in the working directory its keys are used as
 * defaults for the options below (command-line arguments always win). The {@code query} subcommand
 * provides point queries without a full search.
 *
 * <pre>
 *   ./gradlew run --args="--seed 188188 --search-radius 10000 --cluster-radius 128 --min-structures 1"
 *   ./gradlew run --args="--seed 188188 --full-world --tile-size 100000 --tile-overlap 1000"
 *   ./gradlew run --args="query --seed 188188 --coords 544,166 1000,-2000 --radius 1000"
 * </pre>
 */
@Command(
        name = "trialfinder",
        mixinStandardHelpOptions = true,
        subcommands = {QueryCommand.class},
        version = "TrialSpawnerFinder 1.4.0",
        description = "Find dense trial-chamber clusters for a seed. CUDA-accelerated when available.")
public final class TrialFinderCLI implements Callable<Integer> {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Spec
    CommandSpec spec;

    /** World seed; boxed so a missing value (no CLI arg, no finder.properties) can be detected. */
    @Option(names = "--seed", description = "World seed")
    Long seed;

    @Option(names = "--search-radius", defaultValue = "10000",
            description = "Search radius in blocks around (0,0); ignored with --full-world")
    int searchRadius;

    @Option(names = "--cluster-radius", defaultValue = "1000", description = "Density cluster radius in blocks")
    int clusterRadius;

    @Option(names = "--min-structures", defaultValue = "3", description = "Minimum chambers in a cluster")
    int minStructures;

    @Option(names = "--min-spawners", defaultValue = "20", description = "Minimum trial spawners in the density circle")
    int minSpawners;

    @Option(names = "--full-world", defaultValue = "false",
            description = "Scan the full 60M x 60M world square, streaming over tiles")
    boolean fullWorld;

    @Option(names = "--tile-size", defaultValue = "100000",
            description = "Full-world tile edge length in blocks (smaller = more tiles, less RAM)")
    int tileSize;

    @Option(names = "--tile-overlap", defaultValue = "1000",
            description = "Full-world tile enumeration overlap in blocks (avoids boundary misses)")
    int tileOverlap;

    @Option(names = "--top-k", defaultValue = "0", hidden = true,
            description = "Top-K coarse-cluster cap for B-flow (0 = disabled). Keeps only the K "
                    + "largest coarse clusters (all their member chambers) before running Jigsaw generation.")
    int topK;

    @Option(names = "--prefilter-mode", defaultValue = "cluster",
            description = "Prefilter method: cluster (default, density-peak + coarse clustering) "
                    + "or grid (GPU grid aggregation + lossless density pruning, faster for large radii).")
    String prefilterMode;

    @Option(names = "--grid-size", defaultValue = "0",
            description = "Grid cell side in blocks for --prefilter-mode grid (0 = auto: 2*cluster-radius).")
    int gridSize;

    @Option(names = "--output-prefix", description = "Output file prefix (default results-<timestamp>)")
    String outputPrefix;

    @Option(names = "--threads", defaultValue = "4", description = "CPU threads for jigsaw assembly")
    int threads;

    @Option(names = "--debug", defaultValue = "false", description = "Print progress and timing")
    boolean debug;

    @Option(names = "--no-gpu", defaultValue = "false", description = "Force the pure-CPU path")
    boolean noGpu;

    @Option(names = "--quiet", defaultValue = "false",
            description = "Suppress all progress output (stage names and bars)")
    boolean quiet;

    @Option(names = "--biome-check", defaultValue = "false",
            description = "Filter candidates by biome (#has_structure/trial_chambers). Approximate: "
                    + "excludes oceans/deep-oceans, keeps the broad land set.")
    boolean biomeCheck;

    @Option(names = "--cache-dir", defaultValue = "./cache",
            description = "Directory for the B-flow chamber cache (spawner positions + mobs per chamber)")
    String cacheDir;

    @Option(names = "--cache", defaultValue = "false",
            description = "Enable the on-disk B-flow chamber cache (default: disabled)")
    boolean cacheEnabled;

    @Option(names = "--min-candidates-per-tile", defaultValue = "0",
            description = "Sparse-tile prefilter: skip coarse clustering when a tile has fewer "
                    + "density-surviving candidates than this (0 = auto = --min-structures)")
    int minCandidatesPerTile;

    @Option(names = "--jigsaw-depth", defaultValue = "0",
            description = "Shallow jigsaw assembly depth (0 = vanilla depth 20). A smaller value "
                    + "truncates decorative recursion and speeds up B-flow but may drop spawners.")
    int jigsawDepth;

    @Option(names = "--predict-depth", defaultValue = "0",
            description = "Predict-and-verify prefilter depth (0 = disabled). A chamber whose "
                    + "shallow checkpoint spawner count is below --predict-gate is dropped before "
                    + "the full deep assembly; surviving chambers are generated exactly. Approximate: "
                    + "may drop chambers whose real spawner count is higher.")
    int predictDepth;

    @Option(names = "--predict-gate", defaultValue = "0",
            description = "Predict-and-verify gate (min predicted spawners to keep a chamber). "
                    + "Only meaningful with --predict-depth > 0; 0 = keep everything (no effect).")
    int predictGate;

    @Option(names = "--check-top", defaultValue = "0",
            description = "Inspect the top N results: per result, re-generate its chambers and tally "
                    + "fast/slow trial spawners and vaults (appended to the CSV/TXT output). "
                    + "0 (default) disables the check.")
    int checkTop;

    @Option(names = "--auto-tune", defaultValue = "true", fallbackValue = "true",
            description = "Automatically tune cluster-radius/grid-size from search-radius "
                    + "when unset (default: enabled)")
    boolean autoTune = true;

    @Option(names = "--no-auto-tune", defaultValue = "false",
            description = "Disable automatic parameter tuning (keeps the explicit/default values)")
    boolean noAutoTune;

    /** True when {@code finder.properties} supplied a tuning-relevant value (so auto-tune must not override it). */
    private boolean clusterRadiusFromProperties;
    private boolean gridSizeFromProperties;

    private ProgressRenderer progress = new ProgressRenderer();

    /** Timestamp of the last full-world tile-progress line (rate-limited to every 10 tiles / 500 ms). */
    private volatile long lastTileReportNanos = 0;

    public static void main(String[] args) {
        int code = new CommandLine(new TrialFinderCLI()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() throws Exception {
        this.progress = new ProgressRenderer();
        this.progress.setQuiet(this.quiet);

        loadProperties();
        if (this.seed == null) {
            System.err.println("Missing required option '--seed=<seed>'"
                    + " (and no 'seed' in finder.properties in the working directory).");
            return 2;
        }

        applyAutoTune();

        Accelerator acc = selectAccelerator(this.noGpu);
        SpawnerCache cache = null;
        if (this.cacheEnabled) {
            cache = new SpawnerCache(Path.of(this.cacheDir), true, this.debug);
        }
        SearchEngine.Options opts = new SearchEngine.Options(
                seed, searchRadius, clusterRadius, minStructures, minSpawners,
                fullWorld, threads, debug, tileSize, tileOverlap,
                topK, prefilterMode, gridSize, cache, minCandidatesPerTile, jigsawDepth,
                predictDepth, predictGate);

        System.out.println("=== Trial Chambers Finder ===");
        System.out.println("accelerator : " + acc.name());
        System.out.println("seed        : " + seed);
        if (fullWorld) {
            System.out.println("search      : full world (tile " + tileSize + " overlap " + tileOverlap + ")");
        } else {
            System.out.printf("search      : circle radius %,d blocks%n", searchRadius);
        }
        System.out.printf("cluster     : radius=%d min-structures=%d min-spawners=%d%n",
                clusterRadius, minStructures, minSpawners);
        if (topK > 0) {
            System.out.printf("top-k       : %,d coarse clusters (all member chambers)%n", topK);
        }
        if (prefilterMode.equalsIgnoreCase("grid") && topK > 0) {
            System.out.printf("prefilter   : grid (cell %d blocks)%n", opts.effectiveGridSize());
        }
        // 完整配置摘要：显示全部生效参数（命令行 / finder.properties / 默认 融合后的最终值）。
        System.out.println("config      : ----");
        System.out.printf("config      : seed=%d searchRadius=%d clusterRadius=%d minStructures=%d minSpawners=%d%n",
                seed, searchRadius, clusterRadius, minStructures, minSpawners);
        System.out.printf("config      : fullWorld=%s threads=%d tileSize=%d tileOverlap=%d%n",
                fullWorld, threads, tileSize, tileOverlap);
        System.out.printf("config      : topK=%d prefilterMode=%s gridSize=%d%n",
                topK, prefilterMode, opts.effectiveGridSize());
        System.out.printf("config      : outputPrefix=%s cache=%s cacheDir=%s jigsawDepth=%d biomeCheck=%s%n",
                outputPrefix != null ? outputPrefix : "results-<时间戳>",
                cacheEnabled ? "on" : "off", cacheDir, jigsawDepth, biomeCheck);
        System.out.printf("config      : predictDepth=%d predictGate=%d%n", predictDepth, predictGate);
        System.out.printf("config      : gpu=%s debug=%s quiet=%s minCandidatesPerTile=%d%n",
                noGpu ? "off" : "on", debug, quiet, opts.effectiveMinCandidatesPerTile());
        System.out.println("config      : ----");
        cn.trialfinder.sim.biome.BiomeChecker biomeChecker = null;
        if (biomeCheck) {
            biomeChecker = cn.trialfinder.sim.biome.BiomeCheckerFactory.create();
            if (!biomeChecker.isAvailable()) {
                System.out.println("[WARN] --biome-check 生物群系噪声路由器不可用，已跳过过滤。");
                biomeChecker = null;
            }
        }

        boolean grid = prefilterMode.equalsIgnoreCase("grid");
        if (grid) {
            if (fullWorld) {
                return runFullWorld(acc, opts);   // runFullWorld dispatches per-tile to searchRegionGrid
            }
            return runSingleRegionGrid(acc, opts);
        }
        if (topK > 0) {
            if (fullWorld) {
                return runFullWorldTopK(acc, opts);
            }
            return runSingleRegionTopK(acc, opts, biomeChecker);
        }
        if (fullWorld) {
            return runFullWorld(acc, opts);
        }
        return runSingleRegion(acc, opts, biomeChecker);
    }

    // ---------------------------------------------------------------- single region (grid prefilter)

    private Integer runSingleRegionGrid(Accelerator acc, SearchEngine.Options opts) throws IOException {
        SearchEngine.Result result = SearchEngine.runGrid(opts, acc, System.out, this.progress);

        String prefix = outputPrefix != null
                ? outputPrefix
                : "results-" + TIMESTAMP.format(LocalDateTime.now());
        Path csv = Path.of(prefix + ".csv");
        this.progress.setStage(ProgressRenderer.STAGE_OUTPUT);
        writeResultsWithCheck(csv, result.results(), this.checkTop > 0 ? newGenerator(opts) : null);
        this.progress.stageDone(result.resultCount());

        System.out.printf("candidates  : %,d%n", result.candidateCount());
        System.out.printf("pruned      : %,d (grid prefilter)%n", result.prunedCount());
        System.out.printf("clusters    : %,d%n", result.clusterCount());
        System.out.printf("results     : %,d%n", result.resultCount());
        System.out.printf("spawners    : %,d total%n", result.totalSpawners());
        System.out.println("output      : " + csv.toAbsolutePath());
        return 0;
    }

    // ---------------------------------------------------------------- single region

    private Integer runSingleRegion(Accelerator acc, SearchEngine.Options opts,
                                    cn.trialfinder.sim.biome.BiomeChecker biomeChecker) throws IOException {
        SearchEngine.Result result = SearchEngine.run(opts, acc, System.out, this.progress, biomeChecker);

        String prefix = outputPrefix != null
                ? outputPrefix
                : "results-" + TIMESTAMP.format(LocalDateTime.now());
        Path csv = Path.of(prefix + ".csv");
        this.progress.setStage(ProgressRenderer.STAGE_OUTPUT);
        writeResultsWithCheck(csv, result.results(), this.checkTop > 0 ? newGenerator(opts) : null);
        this.progress.stageDone(result.resultCount());

        System.out.printf("candidates  : %,d%n", result.candidateCount());
        System.out.printf("pruned      : %,d (density pre-filter)%n", result.prunedCount());
        System.out.printf("clusters    : %,d%n", result.clusterCount());
        System.out.printf("results     : %,d%n", result.resultCount());
        System.out.printf("spawners    : %,d total%n", result.totalSpawners());
        System.out.println("output      : " + csv.toAbsolutePath());
        return 0;
    }

    // ---------------------------------------------------------------- single region (top-K clusters)

    private Integer runSingleRegionTopK(Accelerator acc, SearchEngine.Options opts,
                                        cn.trialfinder.sim.biome.BiomeChecker biomeChecker) throws IOException {
        SimChamberGenerator generator = newGenerator(opts);
        long radiusSq = (long) opts.searchRadius() * opts.searchRadius();
        SearchRegion region = new SearchRegion(
                -opts.searchRadius(), opts.searchRadius(),
                -opts.searchRadius(), opts.searchRadius(), 0);

        List<SearchEngine.CoarseCluster> clusters = SearchEngine.coarseClustersForRegion(
                region, null, opts, acc, true, 0, 0, radiusSq, this.progress);

        // Bounded min-heap instead of a full sort: keeps exactly the top-K coarse clusters.
        long tRetain = System.nanoTime();
        List<SearchEngine.CoarseCluster> retained = SearchEngine.retainTopK(clusters, topK);
        if (opts.debug()) {
            System.out.printf("[DEBUG] topK retain took %.1f ms (coarse %d -> retained %d, top %d)%n",
                    (System.nanoTime() - tRetain) / 1e6, clusters.size(), retained.size(), topK);
        }
        System.out.printf("coarse clusters %,d -> retained %,d (top %,d clusters)%n",
                clusters.size(), retained.size(), topK);

        List<cn.trialfinder.model.SearchResult> results =
                SearchEngine.generateClusters(retained, generator, opts, this.progress, biomeChecker);

        String prefix = outputPrefix != null
                ? outputPrefix
                : "results-" + TIMESTAMP.format(LocalDateTime.now());
        Path csv = Path.of(prefix + ".csv");
        this.progress.setStage(ProgressRenderer.STAGE_OUTPUT);
        writeResultsWithCheck(csv, results, generator);
        this.progress.stageDone(results.size());
        System.out.printf("results     : %,d complete clusters (spawners >= %,d)%n",
                results.size(), opts.minSpawners());
        System.out.println("output      : " + csv.toAbsolutePath());
        return 0;
    }

    // ---------------------------------------------------------------- full world (top-K clusters streaming)

    private Integer runFullWorldTopK(Accelerator acc, SearchEngine.Options opts) throws Exception {
        WorldTiler tiler = new WorldTiler(opts.tileSize(), opts.tileOverlap());
        List<SearchRegion> tiles = tiler.getTiles();
        SimChamberGenerator generator = newGenerator(opts);

        // Bounded min-heap of coarse clusters keeps the global top-K deterministically.
        PriorityQueue<SearchEngine.CoarseCluster> heap =
                new PriorityQueue<>(SearchEngine.COARSE_WORST_FIRST);

        int totalTiles = tiles.size();
        long totalClusters = 0;
        long totalChambers = 0;
        long started = System.nanoTime();
        this.lastTileReportNanos = 0;
        this.progress.setStage(ProgressRenderer.STAGE_GLOBAL_TOP_K);
        for (int i = 0; i < totalTiles; i++) {
            SearchRegion region = tiles.get(i);
            List<SearchEngine.CoarseCluster> clusters = SearchEngine.coarseClustersForRegion(
                    region, tiler, opts, acc, false, 0, 0, 0, this.progress);
            totalClusters += clusters.size();
            totalChambers += clusters.stream().mapToLong(SearchEngine.CoarseCluster::size).sum();
            for (SearchEngine.CoarseCluster cluster : clusters) {
                heap.add(cluster);
                if (heap.size() > topK) {
                    heap.poll();
                }
            }

            // Global top-K tile progress (rate-limited internally).
            int current = i + 1;
            this.progress.setStage(ProgressRenderer.STAGE_GLOBAL_TOP_K);
            reportTile(current, totalTiles, totalChambers, heap.size(), started);
            if (opts.debug() && !this.progress.isQuiet() && !heap.isEmpty()) {
                System.out.printf("[TopK] heap=%d minScore=%d%n",
                        heap.size(), heap.peek().size());
            }
            if (current % 1000 == 0) {
                System.gc();
            }
        }

        long tRetain = System.nanoTime();
        List<SearchEngine.CoarseCluster> retained = new ArrayList<>(heap);
        retained.sort(SearchEngine.COARSE_BEST_FIRST);
        long retainedChambers = retained.stream().mapToLong(SearchEngine.CoarseCluster::size).sum();
        if (opts.debug()) {
            System.out.printf("[DEBUG] topK retain took %.1f ms (coarse %,d -> retained %,d)%n",
                    (System.nanoTime() - tRetain) / 1e6, totalClusters, retained.size());
        }
        System.out.printf("coarse clusters %,d -> retained %,d (top %,d clusters, %,d chambers)%n",
                totalClusters, retained.size(), topK, retainedChambers);

        long genStart = System.nanoTime();
        List<cn.trialfinder.model.SearchResult> results =
                SearchEngine.generateClusters(retained, generator, opts, this.progress);
        System.out.printf("B-flow generated %,d chambers in %s%n", retainedChambers,
                ProgressRenderer.formatDurationNanos(System.nanoTime() - genStart));

        String prefix = outputPrefix != null
                ? outputPrefix
                : "results-" + TIMESTAMP.format(LocalDateTime.now());
        Path csv = Path.of(prefix + ".csv");
        this.progress.setStage(ProgressRenderer.STAGE_OUTPUT);
        writeResultsWithCheck(csv, results, generator);
        this.progress.stageDone(results.size());
        System.out.printf("results     : %,d complete clusters (spawners >= %,d)%n",
                results.size(), opts.minSpawners());
        System.out.printf("tiles       : %,d%n", tiles.size());
        System.out.println("output      : " + csv.toAbsolutePath());
        return 0;
    }

    // ---------------------------------------------------------------- full world (streaming)

    private Integer runFullWorld(Accelerator acc, SearchEngine.Options opts) throws Exception {
        WorldTiler tiler = new WorldTiler(opts.tileSize(), opts.tileOverlap());
        List<SearchRegion> tiles = tiler.getTiles();
        SimChamberGenerator generator = newGenerator(opts);

        Path tmpDir = Files.createTempDirectory("trialfinder-");
        List<Path> tempFiles = new ArrayList<>();
        long totalCandidates = 0;
        long totalChambers = 0;
        long totalSpawners = 0;
        long started = System.nanoTime();
        int totalTiles = tiles.size();
        this.lastTileReportNanos = 0;
        this.progress.setStage(ProgressRenderer.STAGE_FULL_WORLD);

        try {
            for (int i = 0; i < totalTiles; i++) {
                SearchRegion region = tiles.get(i);
                SearchEngine.RegionStats stats = new SearchEngine.RegionStats();
                List<ResultEntry> entries = opts.isGridPrefilter()
                        ? SearchEngine.searchRegionGrid(generator, region, tiler, opts, acc, stats, this.progress)
                        : SearchEngine.searchRegion(generator, region, tiler, opts, acc, stats, this.progress);

                if (!entries.isEmpty()) {
                    Path tmp = tmpDir.resolve("results_tile_" + region.tileId() + ".tmp");
                    SearchEngine.writeTileTempFile(tmp, entries);
                    tempFiles.add(tmp);
                }
                totalCandidates += stats.candidateCount;
                totalChambers += stats.chamberCount;
                totalSpawners += stats.spawnerCount;

                // Full-world tile progress (rate-limited internally; debug forces every tile).
                int current = i + 1;
                this.progress.setStage(ProgressRenderer.STAGE_FULL_WORLD);
                reportTile(current, totalTiles, totalChambers, totalSpawners, started);
                if (current % 1000 == 0) {
                    System.gc(); // hint: release tile-local structures
                }
            }

            String prefix = outputPrefix != null
                    ? outputPrefix
                    : "results-" + TIMESTAMP.format(LocalDateTime.now());
            Path csv = Path.of(prefix + ".csv");
            this.progress.setStage(ProgressRenderer.STAGE_OUTPUT);
            System.out.println("合并 %d 个分片临时文件...".formatted(tempFiles.size()));
            ResultMerger.merge(tempFiles, csv, 100);
            this.progress.stageDone(tempFiles.size());

            System.out.printf("candidates  : %,d%n", totalCandidates);
            System.out.printf("chambers    : %,d (B-flow generated)%n", totalChambers);
            System.out.printf("spawners    : %,d total%n", totalSpawners);
            System.out.printf("tiles       : %,d%n", tiles.size());
            System.out.println("output      : " + csv.toAbsolutePath());
            return 0;
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort
                        }
                    });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    /**
     * Renders the full-world tile progress line. Rate-limited to every 10 tiles or 500 ms
     * (unless {@code --debug} forces every tile); the last tile always prints a final 100% line.
     *
     * @param current       tile index (1-based)
     * @param total         total tile count
     * @param chambers      accumulated chamber count
     * @param secondMetric  second aggregate to show (spawners in the streaming path, retained
     *                      top-K cluster count in the Top-K path)
     * @param startedNanos  loop start timestamp for ETA estimation
     */
    private void reportTile(int current, int total, long chambers, long secondMetric, long startedNanos) {
        if (this.progress.isQuiet()) {
            return;
        }
        long now = System.nanoTime();
        boolean every10 = current % 10 == 0;
        boolean timeElapsed = now - this.lastTileReportNanos >= 500_000_000L;
        if (current < total && !this.debug && !every10 && !timeElapsed) {
            return;
        }
        double elapsedSec = Math.max(0.001, (now - startedNanos) / 1_000_000_000.0);
        double rate = current / elapsedSec; // tiles per second
        double remainingSec = (total - current) / rate;
        String eta = ProgressRenderer.formatDurationNanos(Math.round(remainingSec * 1_000_000_000.0));
        this.progress.updateTile(current, total, chambers, secondMetric, now - startedNanos, eta);
        this.lastTileReportNanos = now;
    }

    /** Selects the GPU accelerator (unless disabled) or falls back to the CPU reference. */
    static Accelerator selectAccelerator(boolean noGpu) {
        if (!noGpu) {
            try {
                GpuAccelerator gpu = GpuAccelerator.create();
                System.out.println("GPU detected: " + gpu.deviceName());
                return gpu;
            } catch (Throwable t) {
                System.out.println("CUDA unavailable (" + t.getClass().getSimpleName()
                        + ": " + t.getMessage() + ") — falling back to CPU.");
            }
        }
        return new CpuAccelerator();
    }

    /**
     * Builds a generator wired to the B-flow cache when {@code options.cache()} is non-null (i.e.
     * the CLI was invoked with {@code --cache}). Used by the top-K / full-world paths that assemble
     * chambers themselves; the plain single-region paths get the cache inside {@link SearchEngine}.
     */
    private SimChamberGenerator newGenerator(SearchEngine.Options opts) {
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
     * Writes {@code results} to {@code csv} (+ its aligned TXT), optionally inspecting the top
     * {@code --check-top} results and appending their fast/slow-spawner and vault tallies as extra
     * columns. When {@code checkTop <= 0} this behaves exactly like {@code SearchEngine.writeResults}.
     */
    private void writeResultsWithCheck(Path csv, List<cn.trialfinder.model.SearchResult> results,
                                       SimChamberGenerator generator) throws IOException {
        if (this.checkTop <= 0 || generator == null) {
            SearchEngine.writeResults(csv, results);
            return;
        }
        List<cn.trialfinder.cli.CheckTopChecker.CheckResult> checks =
                cn.trialfinder.cli.CheckTopChecker.check(this.seed, results, generator, this.checkTop);
        SearchEngine.writeResults(csv, results, checks);
        int checked = checks.size();
        long fast = checks.stream().mapToLong(cn.trialfinder.cli.CheckTopChecker.CheckResult::fastSpawners).sum();
        long slow = checks.stream().mapToLong(cn.trialfinder.cli.CheckTopChecker.CheckResult::slowSpawners).sum();
        long vaults = checks.stream().mapToLong(cn.trialfinder.cli.CheckTopChecker.CheckResult::vaults).sum();
        System.out.printf("check-top   : %d results | fast=%d slow=%d vaults=%d%n", checked, fast, slow, vaults);
    }

    /**
     * Auto-tunes {@code cluster-radius} and {@code grid-size} from {@code search-radius} when they
     * were not explicitly set (CLI arg or finder.properties) and {@code --auto-tune} is enabled
     * (the default). {@code top-k} is intentionally not tuned: it stays 0 (disabled) so every
     * coarse cluster runs through B flow for maximum precision.
     *
     * <pre>
     *   cluster-radius = max(64, min(256, searchRadius / 200))
     *   grid-size      = 2 * cluster-radius
     * </pre>
     *
     * <p>Skipped in {@code --full-world} mode: {@code search-radius} is ignored there.
     * Tuned values are logged under {@code --debug}.
     */
    private void applyAutoTune() {
        if (!this.autoTune || this.noAutoTune) {
            return;
        }
        if (this.fullWorld) {
            if (this.debug) {
                System.out.println("[auto-tune] skipped for --full-world (search-radius is ignored); "
                        + "pick --cluster-radius explicitly");
            }
            return;
        }
        CommandLine.ParseResult parsed = this.spec.commandLine().getParseResult();

        // Note: grid prefilter is never selected by default — it is used only when the user
        // explicitly passes --prefilter-mode grid (CLI or finder.properties). Large search radii
        // keep the cluster prefilter (density-peak + coarse clustering) so the default result set
        // stays exact; users who want the faster GPU grid path opt in explicitly.

        boolean clusterExplicit = parsed.hasMatchedOption("--cluster-radius")
                || this.clusterRadiusFromProperties;

        if (!clusterExplicit) {
            int tuned = Math.max(64, Math.min(256, this.searchRadius / 200));
            if (tuned != this.clusterRadius) {
                if (this.debug) {
                    System.out.printf("[auto-tune] cluster-radius: %,d -> %,d%n",
                            this.clusterRadius, tuned);
                }
                this.clusterRadius = tuned;
            }
        }
        if (!parsed.hasMatchedOption("--grid-size") && !this.gridSizeFromProperties && this.gridSize == 0) {
            int tuned = 2 * this.clusterRadius;
            if (this.debug) {
                System.out.printf("[auto-tune] grid-size: %d -> %d%n", this.gridSize, tuned);
            }
            this.gridSize = tuned;
        }
        // top-k is intentionally NOT auto-tuned: the default (0 = disabled) runs every coarse
        // cluster through B flow for maximum precision. Users who explicitly want truncation set
        // --top-k themselves.
    }

    /**
     * Loads {@code finder.properties} from the working directory (if present) and applies its keys
     * as defaults for any option the user did <em>not</em> set on the command line. The property
     * keys use the Mod-mode names; command-line arguments always take precedence.
     */
    private void loadProperties() {
        Path file = Path.of("finder.properties");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            System.out.println("[WARN] 无法读取 finder.properties: " + e.getMessage());
            return;
        }
        CommandLine.ParseResult parsed = this.spec.commandLine().getParseResult();
        applyIfUnset(parsed, properties, "seed", "--seed", v -> this.seed = Long.parseLong(v));
        applyIfUnset(parsed, properties, "search-radius-blocks", "--search-radius",
                v -> this.searchRadius = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "cluster-radius-blocks", "--cluster-radius",
                v -> {
                    this.clusterRadius = Integer.parseInt(v);
                    this.clusterRadiusFromProperties = true;
                });
        applyIfUnset(parsed, properties, "min-structures", "--min-structures",
                v -> this.minStructures = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "min-spawners", "--min-spawners",
                v -> this.minSpawners = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "full-world", "--full-world",
                v -> this.fullWorld = Boolean.parseBoolean(v));
        applyIfUnset(parsed, properties, "scan-threads", "--threads",
                v -> this.threads = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "scan-shard-size-blocks", "--tile-size",
                v -> this.tileSize = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "biome-check", "--biome-check",
                v -> this.biomeCheck = Boolean.parseBoolean(v));

        // Full-world / top-K / clustering / prefilter.
        applyIfUnset(parsed, properties, "tile-overlap-blocks", "--tile-overlap",
                v -> this.tileOverlap = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "top-k", "--top-k",
                v -> this.topK = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "prefilter-mode", "--prefilter-mode",
                v -> this.prefilterMode = v);
        applyIfUnset(parsed, properties, "grid-size", "--grid-size",
                v -> {
                    this.gridSize = Integer.parseInt(v);
                    this.gridSizeFromProperties = true;
                });
        applyIfUnset(parsed, properties, "min-candidates-per-tile", "--min-candidates-per-tile",
                v -> this.minCandidatesPerTile = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "jigsaw-depth", "--jigsaw-depth",
                v -> this.jigsawDepth = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "predict-depth", "--predict-depth",
                v -> this.predictDepth = Integer.parseInt(v));
        applyIfUnset(parsed, properties, "predict-gate", "--predict-gate",
                v -> this.predictGate = Integer.parseInt(v));

        // Output / behaviour.
        applyIfUnset(parsed, properties, "output-prefix", "--output-prefix",
                v -> this.outputPrefix = v);
        applyIfUnset(parsed, properties, "debug", "--debug",
                v -> this.debug = Boolean.parseBoolean(v));
        applyIfUnset(parsed, properties, "no-gpu", "--no-gpu",
                v -> this.noGpu = Boolean.parseBoolean(v));
        applyIfUnset(parsed, properties, "quiet", "--quiet",
                v -> this.quiet = Boolean.parseBoolean(v));
        applyIfUnset(parsed, properties, "cache-dir", "--cache-dir",
                v -> this.cacheDir = v);
        applyIfUnset(parsed, properties, "cache", "--cache",
                v -> this.cacheEnabled = Boolean.parseBoolean(v));
        applyIfUnset(parsed, properties, "no-auto-tune", "--no-auto-tune",
                v -> this.noAutoTune = Boolean.parseBoolean(v));

        System.out.println("[config] loaded defaults from " + file.toAbsolutePath());
    }

    /** Applies a property value to an option field only when the option was not given on the CLI. */
    private static void applyIfUnset(CommandLine.ParseResult parsed, Properties properties,
                                     String propKey, String optionName, Consumer<String> setter) {
        if (parsed.hasMatchedOption(optionName)) {
            return;
        }
        String value = properties.getProperty(propKey);
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }
}
