package cn.trialfinder.search;

import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.model.BlockPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class ShardedClusterScanner {
    private ShardedClusterScanner() {
    }

    static ScanResult scan(FinderConfig config) {
        List<Shard> shards = shards(config);
        Map<List<BlockPoint>, CircleClusters.StructureCluster> unique = new LinkedHashMap<>();
        long candidateCount = 0;
        int completed = 0;
        int nextReportPercent = 1;
        long startedNanos = System.nanoTime();
        long estimatedCandidates = estimatedCandidateCount(config);

        try (ExecutorService executor = Executors.newFixedThreadPool(config.scanThreads())) {
            CompletionService<ShardResult> completion = new ExecutorCompletionService<>(executor);
            int next = 0;
            int running = 0;
            while (next < shards.size() || running > 0) {
                while (next < shards.size() && running < config.scanThreads() * 2) {
                    Shard shard = shards.get(next++);
                    completion.submit(() -> scanShard(config, shard));
                    running++;
                }
                ShardResult result = completion.take().get();
                running--;
                completed++;
                candidateCount += result.candidateCount();
                for (CircleClusters.StructureCluster cluster : result.clusters()) {
                    unique.merge(cluster.structures(), cluster, ShardedClusterScanner::minimumCenter);
                }
                int percent = completed * 100 / shards.size();
                if (completed == shards.size() || percent >= nextReportPercent) {
                    System.out.println(progressLine(completed, shards.size(), candidateCount,
                            estimatedCandidates, System.nanoTime() - startedNanos));
                    while (nextReportPercent <= percent) {
                        nextReportPercent++;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("快速扫描被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("快速扫描失败", e.getCause());
        }
        return new ScanResult(candidateCount, new ArrayList<>(unique.values()));
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
        return new ShardResult(coreCandidates, owned);
    }

    private static List<Shard> shards(FinderConfig config) {
        long minX = config.searchMinX();
        long maxX = config.searchMaxX();
        long minZ = config.searchMinZ();
        long maxZ = config.searchMaxZ();
        long size = config.scanShardSizeBlocks();
        List<Shard> result = new ArrayList<>();
        for (long x = minX; x <= maxX; x += size) {
            long shardMaxX = Math.min(maxX, x + size - 1);
            for (long z = minZ; z <= maxZ; z += size) {
                result.add(new Shard(x, shardMaxX, z, Math.min(maxZ, z + size - 1)));
            }
        }
        return result;
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

    static String progressLine(
            int completed, int total, long candidates, long estimatedCandidates, long elapsedNanos) {
        int barWidth = 10;
        int filled = total == 0 ? barWidth : (int) ((long) completed * barWidth / total);
        int percent = total == 0 ? 100 : (int) Math.round(completed * 100.0 / total);
        double elapsedSeconds = Math.max(0.001, elapsedNanos / 1_000_000_000.0);
        long throughput = Math.round(candidates / elapsedSeconds);
        long remainingNanos = completed == total || throughput == 0 ? 0
                : Math.max(0, Math.round((estimatedCandidates - candidates)
                        / (double) throughput * 1_000_000_000.0));
        return String.format(
                "[%s%s] %d%% %d/%d | %s | %s/s | ETA %s",
                "#".repeat(filled), "-".repeat(barWidth - filled), percent,
                completed, total, compact(candidates), compact(throughput), formatDuration(remainingNanos));
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

    private static String compact(long value) {
        if (value >= 1_000_000_000L) return "%.1fB".formatted(value / 1_000_000_000.0);
        if (value >= 1_000_000L) return "%.1fM".formatted(value / 1_000_000.0);
        if (value >= 1_000L) return "%.1fk".formatted(value / 1_000.0);
        return Long.toString(value);
    }

    private static String formatDuration(long nanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(Math.max(0, nanos));
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    record ScanResult(long candidateCount, List<CircleClusters.StructureCluster> clusters) {
    }

    private record Shard(long minX, long maxX, long minZ, long maxZ) {
        boolean owns(long x, long z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private record ShardResult(long candidateCount, List<CircleClusters.StructureCluster> clusters) {
    }
}
