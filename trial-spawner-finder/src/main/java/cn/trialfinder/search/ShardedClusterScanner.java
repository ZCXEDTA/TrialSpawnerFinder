package cn.trialfinder.search;

import cn.trialfinder.config.FinderConfig;
import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ShardedClusterScanner {
    // Keep one batch large enough to amortize scanner/checkpoint overhead while
    // leaving the per-batch candidate and generated-chamber maps bounded.
    static final int MAX_PROCESSING_SHARD_SIZE_BLOCKS = 262_144;
    private static final int SINGLE_STRUCTURE_MAX_SHARD_SIZE_BLOCKS = 32_768;
    private static final int MIN_PROCESSING_SHARD_SIZE_BLOCKS = 16_384;
    private static final int TARGET_SHARDS_PER_THREAD = 2;

    private ShardedClusterScanner() {
    }

    public static ScanResult scan(FinderConfig config) {
        return scan(config, ProgressReporter.NONE);
    }

    public static ScanResult scan(FinderConfig config, ProgressReporter progress) {
        Map<List<BlockPoint>, CircleClusters.StructureCluster> unique = new LinkedHashMap<>();
        ScanSummary summary = scanBatches(config, progress, ignored -> true, batch -> {
            for (CircleClusters.StructureCluster cluster : batch.clusters()) {
                unique.merge(cluster.structures(), cluster, ShardedClusterScanner::minimumCenter);
            }
        });
        return new ScanResult(summary.candidateCount(), new ArrayList<>(unique.values()));
    }

    public static ScanSummary scanBatches(
            FinderConfig config, ProgressReporter progress, BatchConsumer consumer) {
        return scanBatches(config, progress, ignored -> true, consumer);
    }

    public static ScanSummary scanBatches(
            FinderConfig config, ProgressReporter progress,
            IntPredicate shouldProcess, BatchConsumer consumer) {
        List<Shard> shards = shards(config);
        List<Shard> pendingShards = shards.stream()
                .filter(shard -> shouldProcess.test(shard.index()))
                .toList();
        long candidateCount = 0;
        int scanned = 0;
        long scannedCandidates = 0;
        long scanElapsedNanos = 0;
        long estimatedCandidates = Math.max(1, Math.round(
                estimatedCandidateCount(config)
                        * (pendingShards.size() / (double) shards.size())));
        long scanStartedNanos = System.nanoTime();
        if (!pendingShards.isEmpty()) {
            progress.report(ProgressUpdate.estimated(
                    "粗筛", 0, pendingShards.size(), "个", 0, estimatedCandidates));
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(config.scanThreads())) {
            CompletionService<ShardResult> completion = new ExecutorCompletionService<>(executor);
            Map<Integer, ShardResult> ready = new TreeMap<>();
            int nextToSubmit = 0;
            int nextToConsume = 0;
            int running = 0;
            while (nextToConsume < pendingShards.size()) {
                while (nextToSubmit < pendingShards.size()
                        && running < config.scanThreads() * 2) {
                    Shard shard = pendingShards.get(nextToSubmit++);
                    completion.submit(() -> scanShard(config, shard));
                    running++;
                }
                ShardResult result = completion.take().get();
                running--;
                scanned++;
                scannedCandidates += result.candidateCount();
                scanElapsedNanos = Math.max(
                        scanElapsedNanos, result.scanCompletedNanos() - scanStartedNanos);
                progress.report(ProgressUpdate.estimatedAt(
                        "粗筛", scanned, pendingShards.size(), "个",
                        scannedCandidates, estimatedCandidates,
                        scanElapsedNanos));
                ready.put(result.shardIndex(), result);

                while (nextToConsume < pendingShards.size()) {
                    int expected = pendingShards.get(nextToConsume).index();
                    ShardResult ordered = ready.remove(expected);
                    if (ordered == null) break;
                    nextToConsume++;
                    candidateCount += ordered.candidateCount();
                    consumer.accept(new ClusterBatch(
                            ordered.shardIndex(), shards.size(),
                            ordered.candidateCount(), ordered.clusters()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("快速扫描被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("快速扫描失败", e.getCause());
        }
        return new ScanSummary(candidateCount, shards.size());
    }

    private static ShardResult scanShard(FinderConfig config, Shard shard) {
        long margin = overlap(config);
        List<BlockPoint> points = TrialChamberCandidates.enumerate(config,
                shard.minX() - margin, shard.maxX() + margin,
                shard.minZ() - margin, shard.maxZ() + margin);
        List<CircleClusters.StructureCluster> found = switch (config.areaShape()) {
            case CIRCLE -> CircleClusters.find(points, config.clusterRadiusBlocks(), config.minStructures());
            case SQUARE -> SquareClusters.find(points, config.clusterRadiusBlocks(), config.minStructures());
        };
        List<CircleClusters.StructureCluster> owned = found.stream()
                .filter(cluster -> {
                    BlockPoint owner = cluster.structures().getFirst();
                    return shard.owns(owner.x(), owner.z());
                })
                .toList();
        long coreCandidates = points.stream()
                .filter(point -> shard.owns(point.x(), point.z()))
                .count();
        return new ShardResult(shard.index(), coreCandidates, owned, System.nanoTime());
    }

    private static List<Shard> shards(FinderConfig config) {
        long minX = config.searchMinX();
        long maxX = config.searchMaxX();
        long minZ = config.searchMinZ();
        long maxZ = config.searchMaxZ();
        long size = processingShardSizeBlocks(config);
        List<Shard> spatial = new ArrayList<>();
        for (long x = minX; x <= maxX; x += size) {
            long shardMaxX = Math.min(maxX, x + size - 1);
            for (long z = minZ; z <= maxZ; z += size) {
                spatial.add(new Shard(-1, x, shardMaxX, z, Math.min(maxZ, z + size - 1)));
            }
        }
        long centerX = config.fullWorld() ? 0 : config.searchCenterX();
        long centerZ = config.fullWorld() ? 0 : config.searchCenterZ();
        spatial.sort(Comparator
                .comparingLong((Shard shard) -> shard.distanceSquared(centerX, centerZ))
                .thenComparingLong(Shard::minX)
                .thenComparingLong(Shard::minZ));
        List<Shard> result = new ArrayList<>(spatial.size());
        for (int index = 0; index < spatial.size(); index++) {
            Shard shard = spatial.get(index);
            result.add(new Shard(
                    index, shard.minX(), shard.maxX(), shard.minZ(), shard.maxZ()));
        }
        return result;
    }

    static int processingShardSizeBlocks(FinderConfig config) {
        long width = config.searchMaxX() - config.searchMinX() + 1;
        long height = config.searchMaxZ() - config.searchMinZ() + 1;
        long targetShards = Math.max(1L,
                (long) config.scanThreads() * TARGET_SHARDS_PER_THREAD);
        long areaPerShard = Math.ceilDiv(width * height, targetShards);
        long idealSize = (long) Math.ceil(Math.sqrt(areaPerShard));
        int maximumSize = config.minStructures() == 1
                ? SINGLE_STRUCTURE_MAX_SHARD_SIZE_BLOCKS
                : MAX_PROCESSING_SHARD_SIZE_BLOCKS;
        long minimumSize = Math.min(maximumSize,
                Math.max(MIN_PROCESSING_SHARD_SIZE_BLOCKS, overlap(config)));
        return (int) Math.max(minimumSize,
                Math.min(maximumSize, idealSize));
    }

    static int processingShardCount(FinderConfig config) {
        long size = processingShardSizeBlocks(config);
        long columns = Math.ceilDiv(config.searchMaxX() - config.searchMinX() + 1, size);
        long rows = Math.ceilDiv(config.searchMaxZ() - config.searchMinZ() + 1, size);
        return Math.toIntExact(columns * rows);
    }

    private static CircleClusters.StructureCluster minimumCenter(
            CircleClusters.StructureCluster first, CircleClusters.StructureCluster second) {
        int byX = Long.compare(first.center().roundedX(), second.center().roundedX());
        if (byX != 0) return byX < 0 ? first : second;
        return first.center().roundedZ() <= second.center().roundedZ() ? first : second;
    }

    private static long overlap(FinderConfig config) {
        int multiplier = switch (config.areaShape()) {
            case CIRCLE -> 2;
            case SQUARE -> 4;
        };
        return (long) config.clusterRadiusBlocks() * multiplier + 2;
    }

    static long estimatedCandidateCount(FinderConfig config) {
        double area;
        if (config.fullWorld()) {
            area = rectangleArea(config);
        } else {
            long radius = config.searchRadiusBlocks();
            boolean clipped = config.searchMinX() > (long) config.searchCenterX() - radius
                    || config.searchMaxX() < (long) config.searchCenterX() + radius
                    || config.searchMinZ() > (long) config.searchCenterZ() - radius
                    || config.searchMaxZ() < (long) config.searchCenterZ() + radius;
            area = clipped ? clippedCircleArea(config) : Math.PI * radius * radius;
        }
        long regionWidth = (long) TrialChamberCandidates.SPACING_CHUNKS * 16;
        return Math.max(1, Math.round(area / (regionWidth * regionWidth)));
    }

    private static double rectangleArea(FinderConfig config) {
        return (config.searchMaxX() - config.searchMinX())
                * (double) (config.searchMaxZ() - config.searchMinZ());
    }

    private static double clippedCircleArea(FinderConfig config) {
        double radius = config.searchRadiusBlocks();
        double minX = config.searchMinX() - config.searchCenterX();
        double maxX = config.searchMaxX() - config.searchCenterX();
        double minZ = config.searchMinZ() - config.searchCenterZ();
        double maxZ = config.searchMaxZ() - config.searchCenterZ();
        int slices = 16_384;
        double step = (maxX - minX) / slices;
        double area = 0;
        for (int i = 0; i < slices; i++) {
            double x = minX + (i + 0.5) * step;
            double halfHeight = Math.sqrt(Math.max(0, radius * radius - x * x));
            area += Math.max(0, Math.min(maxZ, halfHeight) - Math.max(minZ, -halfHeight)) * step;
        }
        return area;
    }

    public record ScanResult(long candidateCount, List<CircleClusters.StructureCluster> clusters) {
    }

    public record ScanSummary(long candidateCount, int shardCount) {
    }

    public record ClusterBatch(
            int shardIndex, int shardCount, long candidateCount,
            List<CircleClusters.StructureCluster> clusters) {
        public ClusterBatch {
            clusters = List.copyOf(clusters);
        }
    }

    @FunctionalInterface
    public interface BatchConsumer {
        void accept(ClusterBatch batch);
    }

    private record Shard(int index, long minX, long maxX, long minZ, long maxZ) {
        boolean owns(long x, long z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        long distanceSquared(long x, long z) {
            long dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
            long dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
            return dx * dx + dz * dz;
        }
    }

    private record ShardResult(
            int shardIndex, long candidateCount, List<CircleClusters.StructureCluster> clusters,
            long scanCompletedNanos) {
    }
}
