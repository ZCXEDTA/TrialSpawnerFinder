package cn.trialfinder.sim;

import cn.trialfinder.sim.math.ChunkPos;

import java.nio.file.Path;
import java.util.List;

/** Robustness sweep: generate a chamber for every A-flow candidate in a region. */
public final class SimSweepMain {
    private SimSweepMain() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        Path dataDir = Path.of("src/main/resources");
        SimChamberGenerator generator = new SimChamberGenerator(dataDir);
        List<ChunkPos> candidates = generator.enumeratePotentialChunks(seed, -2000, 2000, -2000, 2000);
        int ok = 0;
        int rejected = 0;
        int errors = 0;
        int totalSpawners = 0;
        for (ChunkPos chunk : candidates) {
            try {
                var result = generator.generate(seed, chunk.x(), chunk.z());
                if (result.isPresent()) {
                    ok++;
                    totalSpawners += result.get().spawnerPositions().size();
                } else {
                    rejected++;
                }
            } catch (Exception e) {
                errors++;
                System.out.println("ERROR chunk [" + chunk.x() + "," + chunk.z() + "]: " + e);
            }
        }
        System.out.println("candidates=" + candidates.size() + " ok=" + ok + " rejected=" + rejected
                + " errors=" + errors + " totalSpawners=" + totalSpawners);
    }
}
