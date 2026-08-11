package cn.trialfinder.test;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.search.CircleClusters;
import cn.trialfinder.sim.SimChamberGenerator;
import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.ChunkPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Replicates the original finder pipeline (A flow enumeration → B flow generation → clustering →
 * density) using the standalone {@code cn.trialfinder.sim.*} package, so the sim's results can be
 * compared against a real {@code run.bat} log.
 *
 * <p>The pipeline mirrors {@code FinderSearch}:
 * <ol>
 *   <li><b>A flow</b> — enumerate candidate chamber chunks via the 34×34 grid placement
 *       ({@link TrialChambersData#PLACEMENT});</li>
 *   <li><b>B flow</b> — generate each chamber in parallel via {@link SimChamberGenerator}, collecting
 *       the trial-spawner block positions;</li>
 *   <li>cluster the candidates with {@link CircleClusters} (same algorithm the original uses);</li>
 *   <li>per cluster, find the integer center that maximizes spawners within {@code clusterRadius}
 *       (port of the original {@code ExactCenterOptimizer}, which is package-private in
 *       {@code cn.trialfinder.search}).</li>
 * </ol>
 *
 * <p>Usable as a runnable main or as a JUnit test:
 * <pre>
 *   java cn.trialfinder.test.SearchVerification \
 *       --seed -9206294873968313284 --radius 10000 \
 *       --cluster-radius 128 --min-structures 1 --min-spawners 20
 * </pre>
 */
public final class SearchVerification {
    private static final int WORLD_LIMIT = 30_000_000;
    private static final int SPACING = TrialChambersData.SPACING_CHUNKS;

    /** Runtime configuration. */
    public record Config(long seed, int centerX, int centerZ, int radius,
                         int clusterRadius, int minStructures, int minSpawners) {

        public static Config defaults() {
            return new Config(-9206294873968313284L, 0, 0, 10000, 128, 1, 20);
        }
    }

    /** A qualifying density result, ordered exactly like the original SearchResult. */
    public record SearchResult(long centerX, long centerZ, int structureCount, int spawnerCount,
                               List<BlockPoint> structures) implements Comparable<SearchResult> {
        @Override
        public int compareTo(SearchResult other) {
            int bySpawners = Integer.compare(other.spawnerCount, spawnerCount);
            if (bySpawners != 0) return bySpawners;
            int byStructures = Integer.compare(other.structureCount, structureCount);
            if (byStructures != 0) return byStructures;
            int byX = Long.compare(centerX, other.centerX);
            return byX != 0 ? byX : Long.compare(centerZ, other.centerZ);
        }
    }

    /** Collected metrics for one run. */
    public static final class Report {
        public Config config;
        public int candidateCount;
        public int clusterCount;
        public int resultCount;
        public int totalSpawners;
        public List<SearchResult> results = List.of();
        public List<SearchResult> top5 = List.of();
        public String error = "";
        public boolean ok;
    }

    /**
     * Fast regression test: the A-flow candidate and cluster counts for the real seed must match
     * the original {@code run.bat} log (1062 candidates / 1072 clusters). No chamber generation
     * is performed, so this runs in milliseconds.
     */
    @Test
    void candidateAndClusterCountsMatchOriginalLog() {
        Config cfg = Config.defaults();
        List<BlockPoint> candidates = enumerateCandidates(cfg);
        assertEquals(1062, candidates.size(), "A-flow candidate count must match run.bat log");
        assertEquals(1072, CircleClusters.find(candidates, cfg.clusterRadius(), cfg.minStructures()).size(),
                "cluster count must match run.bat log");
    }

    public static void main(String[] args) {
        try {
            String dumpPath = null;
            int i = 0;
            while (i < args.length) {
                if (args[i].equals("--dump")) {
                    dumpPath = args[i + 1];
                    String[] rest = new String[args.length - 2];
                    System.arraycopy(args, 0, rest, 0, i);
                    System.arraycopy(args, i + 2, rest, i, args.length - i - 2);
                    args = rest;
                } else {
                    i++;
                }
            }
            Config cfg = parseArgs(args);
            Report report = run(cfg);
            print(report);
            if (dumpPath != null) {
                writeDump(dumpPath, report.results);
            }
            if (!report.ok) {
                System.exit(1);
            }
        } catch (Throwable t) {
            System.err.println("SearchVerification FAILED: " + t);
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /** Writes all results as CSV lines matching the original results CSV: rank;X;Z;structures;spawners;positions. */
    private static void writeDump(String path, List<SearchResult> results) {
        try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
                java.nio.file.Path.of(path), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("﻿排名;中心X;中心Z;密室数量;试炼刷怪笼数量;密室位置");
            writer.newLine();
            int rank = 1;
            for (SearchResult r : results) {
                String positions = r.structures().stream().map(p -> p.x() + "," + p.z())
                        .collect(java.util.stream.Collectors.joining("|"));
                writer.write("%d;%d;%d;%d;%d;%s".formatted(
                        rank++, r.centerX(), r.centerZ(), r.structureCount(), r.spawnerCount(), positions));
                writer.newLine();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("dump failed", e);
        }
    }

    // ------------------------------------------------------------ entry points

    public static Report run(Config cfg) {
        Report report = new Report();
        report.config = cfg;
        try {
            SimChamberGenerator generator = SimChamberGenerator.fromClasspath();

            // 1. A flow — enumerate candidate chambers. The original ShardedClusterScanner clusters
            // the search area plus an overlap margin (2*clusterRadius + 2) and keeps clusters whose
            // first member is inside the search rectangle. Replicate that here.
            long margin = 2L * cfg.clusterRadius + 2;
            List<BlockPoint> allCandidates = enumerateCandidates(cfg, margin);
            List<BlockPoint> candidates = allCandidates.stream()
                    .filter(p -> inSearchRectangle(cfg, p))
                    .toList();
            report.candidateCount = candidates.size();

            // 2. B flow — generate each chamber in parallel, collect spawners.
            Map<BlockPoint, List<BlockPos>> spawnersByChamber = generateAll(generator, cfg, allCandidates);
            report.totalSpawners = spawnersByChamber.values().stream().mapToInt(List::size).sum();

            // 3. Cluster the full (margin-expanded) candidate set, keep owned clusters.
            List<CircleClusters.StructureCluster> clusters = CircleClusters.find(
                    allCandidates, cfg.clusterRadius, cfg.minStructures).stream()
                    .filter(c -> inSearchRectangle(cfg, c.structures().getFirst()))
                    .toList();
            report.clusterCount = clusters.size();

            // 4. Density per cluster (uses the real ExactCenterOptimizer + deduplicated spawner
            // set, exactly like the server's FinderSearch).
            List<SearchResult> results = new ArrayList<>();
            for (CircleClusters.StructureCluster cluster : clusters) {
                List<BlockPoint> structures = cluster.structures().stream()
                        .filter(spawnersByChamber::containsKey)
                        .sorted()
                        .toList();
                if (structures.size() < cfg.minStructures) {
                    continue;
                }
                java.util.Set<cn.trialfinder.model.SpawnerPoint> clusterSpawners = new java.util.TreeSet<>();
                structures.forEach(s -> {
                    for (BlockPos p : spawnersByChamber.get(s)) {
                        clusterSpawners.add(new cn.trialfinder.model.SpawnerPoint(p.getX(), p.getY(), p.getZ()));
                    }
                });
                try {
                    cn.trialfinder.search.ExactCenterOptimizer.CenterScore score =
                            cn.trialfinder.search.ExactCenterOptimizer.find(
                                    cn.trialfinder.config.AreaShape.CIRCLE, cfg.clusterRadius,
                                    structures, clusterSpawners);
                    if (score.spawners() >= cfg.minSpawners) {
                        results.add(new SearchResult(score.x(), score.z(), structures.size(),
                                score.spawners(), structures));
                    }
                } catch (IllegalStateException ignored) {
                    // no integer center covers all structures — skip (matches original behavior)
                }
            }
            results.sort(Comparator.naturalOrder());
            // The original FinderSearch.refreshResults limits to 100 results per structure-count
            // group; apply the same so the output matches the server CSV exactly.
            List<SearchResult> limited = results.stream()
                    .collect(java.util.stream.Collectors.groupingBy(SearchResult::structureCount))
                    .values().stream()
                    .flatMap(group -> group.stream().sorted().limit(100))
                    .sorted()
                    .toList();
            report.results = limited;
            report.resultCount = limited.size();
            report.top5 = limited.stream().limit(5).toList();
            report.ok = true;
        } catch (Throwable t) {
            report.error = t.toString();
            report.ok = false;
        }
        return report;
    }

    public static void print(Report report) {
        System.out.println("=== SearchVerification report ===");
        Config cfg = report.config;
        System.out.println("config        : seed=" + cfg.seed() + " center=(" + cfg.centerX() + "," + cfg.centerZ()
                + ") radius=" + cfg.radius() + " cluster-radius=" + cfg.clusterRadius()
                + " min-structures=" + cfg.minStructures() + " min-spawners=" + cfg.minSpawners());
        System.out.println("candidates    : " + report.candidateCount);
        System.out.println("clusters      : " + report.clusterCount);
        System.out.println("results       : " + report.resultCount);
        System.out.println("total spawners: " + report.totalSpawners);
        System.out.println("--- top 5 ---");
        int rank = 1;
        for (SearchResult r : report.top5) {
            String positions = r.structures().stream().map(p -> p.x() + "," + p.z())
                    .collect(java.util.stream.Collectors.joining("|"));
            System.out.printf("rank %d: center=(%d,%d) structures=%d spawners=%d  [%s]%n",
                    rank++, r.centerX(), r.centerZ(), r.structureCount(), r.spawnerCount(), positions);
        }
        System.out.println("result        : " + (report.ok ? "OK" : "FAILED"));
        if (!report.ok) {
            System.out.println("error         : " + report.error);
        }
    }

    // ------------------------------------------------------------ A flow

    private static List<BlockPoint> enumerateCandidates(Config cfg) {
        return enumerateCandidates(cfg, 0);
    }

    /**
     * Enumerates candidate chamber block points in the search area expanded by {@code margin}
     * blocks (matching the original ShardedClusterScanner, which clusters the area + overlap
     * margin). Candidates are still filtered to the search circle.
     */
    private static List<BlockPoint> enumerateCandidates(Config cfg, long margin) {
        long minX = Math.max(-WORLD_LIMIT, (long) cfg.centerX - cfg.radius - margin);
        long maxX = Math.min(WORLD_LIMIT, (long) cfg.centerX + cfg.radius + margin);
        long minZ = Math.max(-WORLD_LIMIT, (long) cfg.centerZ - cfg.radius - margin);
        long maxZ = Math.min(WORLD_LIMIT, (long) cfg.centerZ + cfg.radius + margin);
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
                ChunkPos chunk = TrialChambersData.PLACEMENT.getPotentialStructureChunkFromRegion(cfg.seed(), regionX, regionZ);
                BlockPoint candidate = new BlockPoint(chunk.x() * 16 + 8, chunk.z() * 16 + 8);
                if (candidate.x() >= minX && candidate.x() <= maxX
                        && candidate.z() >= minZ && candidate.z() <= maxZ
                        && containsPoint(cfg, candidate)) {
                    result.add(candidate);
                }
            }
        }
        result.sort(BlockPoint::compareTo);
        return result;
    }

    private static boolean inSearchRectangle(Config cfg, BlockPoint p) {
        long minX = Math.max(-WORLD_LIMIT, (long) cfg.centerX - cfg.radius);
        long maxX = Math.min(WORLD_LIMIT, (long) cfg.centerX + cfg.radius);
        long minZ = Math.max(-WORLD_LIMIT, (long) cfg.centerZ - cfg.radius);
        long maxZ = Math.min(WORLD_LIMIT, (long) cfg.centerZ + cfg.radius);
        return p.x() >= minX && p.x() <= maxX && p.z() >= minZ && p.z() <= maxZ;
    }

    private static boolean containsPoint(Config cfg, BlockPoint p) {
        long dx = (long) p.x() - cfg.centerX();
        long dz = (long) p.z() - cfg.centerZ();
        long r = cfg.radius();
        return dx * dx + dz * dz <= r * r;
    }

    // ------------------------------------------------------------ B flow

    private static Map<BlockPoint, List<BlockPos>> generateAll(
            SimChamberGenerator generator, Config cfg, List<BlockPoint> candidates) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ConcurrentHashMap<BlockPoint, List<BlockPos>> spawners = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (BlockPoint candidate : candidates) {
                futures.add(executor.submit(() -> {
                    int chunkX = Math.floorDiv(candidate.x(), 16);
                    int chunkZ = Math.floorDiv(candidate.z(), 16);
                    SimChamberGenerator.ChamberResult result =
                            generator.generateChamber(cfg.seed(), chunkX, chunkZ).orElse(null);
                    if (result != null && !result.spawnerPositions().isEmpty()) {
                        spawners.put(candidate, result.spawnerPositions());
                    }
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

    // ------------------------------------------------------------ density

    /** Port of the original {@code ExactCenterOptimizer.find} (circle shape only, matching run.bat). */
    private static CenterScore findBestCenter(int radius, List<BlockPoint> structures, List<BlockPos> spawners) {
        int minZ = structures.stream().mapToInt(BlockPoint::z).max().orElseThrow() - radius;
        int maxZ = structures.stream().mapToInt(BlockPoint::z).min().orElseThrow() + radius;
        CenterScore best = null;
        long radiusSquared = (long) radius * radius;
        for (int z = minZ; z <= maxZ; z++) {
            IntRange legal = legalXRange(radius, radiusSquared, structures, z);
            if (legal == null) {
                continue;
            }
            int[] difference = new int[legal.max - legal.min + 2];
            for (BlockPos spawner : spawners) {
                IntRange covered = coveredXRange(radius, radiusSquared, spawner.getX(), spawner.getZ(), z);
                if (covered == null) {
                    continue;
                }
                int from = Math.max(legal.min, covered.min);
                int to = Math.min(legal.max, covered.max);
                if (from <= to) {
                    difference[from - legal.min]++;
                    difference[to - legal.min + 1]--;
                }
            }
            int count = 0;
            for (int index = 0; index < difference.length - 1; index++) {
                count += difference[index];
                CenterScore candidate = new CenterScore(legal.min + index, z, count);
                if (best == null || candidate.compareTo(best) < 0) {
                    best = candidate;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("找不到能包含所有密室起点的整数中心");
        }
        return best;
    }

    private static IntRange legalXRange(int radius, long radiusSquared, List<BlockPoint> structures, int centerZ) {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        for (BlockPoint structure : structures) {
            IntRange range = coveredXRange(radius, radiusSquared, structure.x(), structure.z(), centerZ);
            if (range == null) {
                return null;
            }
            min = Math.max(min, range.min);
            max = Math.min(max, range.max);
            if (min > max) {
                return null;
            }
        }
        return new IntRange(min, max);
    }

    private static IntRange coveredXRange(int radius, long radiusSquared, int pointX, int pointZ, int centerZ) {
        long dz = (long) pointZ - centerZ;
        if (Math.abs(dz) > radius) {
            return null;
        }
        int horizontal = (int) floorSqrt(radiusSquared - dz * dz);
        return new IntRange(pointX - horizontal, pointX + horizontal);
    }

    private static long floorSqrt(long value) {
        long root = (long) Math.sqrt(value);
        while ((root + 1) * (root + 1) <= value) {
            root++;
        }
        while (root * root > value) {
            root--;
        }
        return root;
    }

    private record CenterScore(long x, long z, int spawners) implements Comparable<CenterScore> {
        @Override
        public int compareTo(CenterScore other) {
            int bySpawners = Integer.compare(other.spawners, spawners);
            if (bySpawners != 0) return bySpawners;
            int byX = Long.compare(x, other.x);
            return byX != 0 ? byX : Long.compare(z, other.z);
        }
    }

    private record IntRange(int min, int max) {
    }

    // ------------------------------------------------------------ args

    static Config parseArgs(String[] args) {
        Config cfg = Config.defaults();
        long seed = cfg.seed();
        int centerX = cfg.centerX();
        int centerZ = cfg.centerZ();
        int radius = cfg.radius();
        int clusterRadius = cfg.clusterRadius();
        int minStructures = cfg.minStructures();
        int minSpawners = cfg.minSpawners();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--center-x" -> centerX = Integer.parseInt(args[++i]);
                case "--center-z" -> centerZ = Integer.parseInt(args[++i]);
                case "--radius" -> radius = Integer.parseInt(args[++i]);
                case "--cluster-radius" -> clusterRadius = Integer.parseInt(args[++i]);
                case "--min-structures" -> minStructures = Integer.parseInt(args[++i]);
                case "--min-spawners" -> minSpawners = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        return new Config(seed, centerX, centerZ, radius, clusterRadius, minStructures, minSpawners);
    }

    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
