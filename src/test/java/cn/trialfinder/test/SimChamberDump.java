package cn.trialfinder.test;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.sim.SimChamberGenerator;
import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.ChunkPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Generates every candidate chamber via the standalone sim and dumps block-point → spawner
 * positions in the same format as the server-side {@code trialfinder.dumpchambers} output,
 * for exact per-chamber comparison.
 *
 * <p>Usage: SimChamberDump &lt;out&gt; [seed]
 */
public final class SimChamberDump {
    private SimChamberDump() {
    }

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/sim-chambers.txt";
        long seed = args.length > 1 ? Long.parseLong(args[1]) : -9206294873968313284L;
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();

        List<BlockPoint> candidates = enumerate(seed);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ConcurrentHashMap<String, List<BlockPos>> map = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (BlockPoint candidate : candidates) {
                futures.add(executor.submit(() -> {
                    int chunkX = Math.floorDiv(candidate.x(), 16);
                    int chunkZ = Math.floorDiv(candidate.z(), 16);
                    SimChamberGenerator.ChamberResult result =
                            generator.generateChamber(seed, chunkX, chunkZ).orElse(null);
                    if (result != null) {
                        map.put(candidate.x() + "," + candidate.z(), result.spawnerPositions());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        }

        List<String> lines = new ArrayList<>();
        map.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            StringBuilder sb = new StringBuilder(entry.getKey());
            for (BlockPos s : entry.getValue()) {
                sb.append(';').append(s.getX()).append(',').append(s.getY()).append(',').append(s.getZ());
            }
            lines.add(sb.toString());
        });
        Files.write(Path.of(out), lines);
        System.out.println("sim dumped " + lines.size() + " chambers to " + out);
    }

    private static List<BlockPoint> enumerate(long seed) {
        int minR = -21, maxR = 21;
        List<BlockPoint> result = new ArrayList<>();
        for (int rx = minR; rx <= maxR; rx++) {
            for (int rz = minR; rz <= maxR; rz++) {
                ChunkPos c = TrialChambersData.PLACEMENT.getPotentialStructureChunkFromRegion(seed, rx, rz);
                int x = c.x() * 16 + 8;
                int z = c.z() * 16 + 8;
                if (x >= -10000 && x <= 10000 && z >= -10000 && z <= 10000
                        && (long) x * x + (long) z * z <= 10000L * 10000L) {
                    result.add(new BlockPoint(x, z));
                }
            }
        }
        result.sort(BlockPoint::compareTo);
        return result;
    }
}
