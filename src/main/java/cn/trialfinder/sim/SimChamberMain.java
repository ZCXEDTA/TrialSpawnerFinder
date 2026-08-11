package cn.trialfinder.sim;

import cn.trialfinder.sim.math.ChunkPos;

import java.nio.file.Path;
import java.util.List;

/**
 * Manual verification entry point.
 * Usage: SimChamberMain &lt;seed&gt; [minX maxX minZ maxZ]
 * Prints A-flow candidate chunks and (for the first candidate) the assembled chamber.
 */
public final class SimChamberMain {
    private SimChamberMain() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        long minX = args.length > 4 ? Long.parseLong(args[1]) : -1000;
        long maxX = args.length > 4 ? Long.parseLong(args[2]) : 1000;
        long minZ = args.length > 4 ? Long.parseLong(args[3]) : -1000;
        long maxZ = args.length > 4 ? Long.parseLong(args[4]) : 1000;

        Path dataDir = Path.of("src/main/resources");
        SimChamberGenerator generator = new SimChamberGenerator(dataDir);

        System.out.println("=== A flow: potential structure chunks for seed " + seed + " ===");
        List<ChunkPos> candidates = generator.enumeratePotentialChunks(seed, minX, maxX, minZ, maxZ);
        System.out.println("candidate count: " + candidates.size());
        for (ChunkPos chunk : candidates) {
            System.out.println("  chunk [" + chunk.x() + ", " + chunk.z() + "]  block (" + chunk.getMinBlockX() + ", " + chunk.getMinBlockZ() + ")");
        }

        if (!candidates.isEmpty()) {
            ChunkPos first = candidates.get(0);
            System.out.println();
            System.out.println("=== B flow: assembling chamber at chunk [" + first.x() + ", " + first.z() + "] ===");
            SimChamberGenerator.ChamberResult result = generator.generate(seed, first.x(), first.z()).orElse(null);
            if (result == null) {
                System.out.println("  (chamber rejected: too close to world height limits)");
            } else {
                System.out.println("  pieces: " + result.assembly().pieces().size());
                System.out.println("  overall bbox: " + result.assembly().boundingBox());
                System.out.println("  spawner positions: " + result.spawnerPositions().size());
                result.spawnerPositions().forEach(pos -> System.out.println("    " + pos));
                System.out.println("  mob aliases: " + result.mobAliases());
            }
        }
    }
}
