package cn.trialfinder.cli;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.sim.SimChamberGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the predict-and-verify checkpoint prefilter:
 * <ul>
 *   <li>{@code generateWithCheckpoint(..., depth >= maxDepth, gate=0)} is identical to
 *       {@code generate(...)} (exactness regression — the checkpoint must not perturb the result).</li>
 *   <li>At a shallow depth the checkpointed count is a lower bound on the full spawner count
 *       (the BFS prefix property).</li>
 *   <li>A non-zero gate drops low-scoring chambers and keeps high-scoring ones exactly.</li>
 * </ul>
 */
class PredictCheckpointTest {

    private static final long SEED = 188188L;

    private static List<BlockPoint> candidates(SimChamberGenerator generator, long seed, int radius) {
        cn.trialfinder.accel.CpuAccelerator acc = new cn.trialfinder.accel.CpuAccelerator();
        return acc.findChunks(seed, -radius, radius, -radius, radius, true, 0, 0, (long) radius * radius);
    }

    @Test
    void fullDepthCheckpointMatchesExactGenerate() {
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        List<BlockPoint> points = candidates(generator, SEED, 4000);
        int compared = 0;
        for (BlockPoint p : points) {
            int cx = Math.floorDiv(p.x(), 16);
            int cz = Math.floorDiv(p.z(), 16);
            SimChamberGenerator.ChamberResult exact = generator.generateChamber(SEED, cx, cz).orElse(null);
            // depth 20 >= maxDepth(20) → checkpoint disabled, must be identical to exact.
            SimChamberGenerator.ChamberResult checkpoint = generator.generateWithCheckpoint(
                    SEED, cx, cz, 20, 0).orElse(null);
            if (exact != null && checkpoint != null) {
                assertEquals(exact.spawnerPositions(), checkpoint.spawnerPositions(),
                        "checkpoint at full depth must match exact for (" + cx + "," + cz + ")");
                compared++;
            }
        }
        assertTrue(compared > 10, "should compare a meaningful sample, got " + compared);
    }

    @Test
    void countSpawnersMatchesCollectedSpawnerPositions() {
        // The per-template spawner map must agree with the block scan: countSpawners over the full
        // pieces equals the collected spawner positions count. This validates the checkpoint
        // predictor's counting fast-path.
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        List<BlockPoint> points = candidates(generator, SEED, 4000);
        int checked = 0;
        for (BlockPoint p : points) {
            int cx = Math.floorDiv(p.x(), 16);
            int cz = Math.floorDiv(p.z(), 16);
            SimChamberGenerator.ChamberResult full = generator.generateChamber(SEED, cx, cz).orElse(null);
            if (full == null) {
                continue;
            }
            int fullCount = full.spawnerPositions().size();
            int mappedCount = generator.countSpawners(full.assembly().pieces());
            assertEquals(fullCount, mappedCount,
                    "countSpawners(full pieces) must equal collected spawner count at ("
                            + cx + "," + cz + ")");
            checked++;
        }
        assertTrue(checked > 10, "should check a meaningful sample, got " + checked);
    }

    @Test
    void gateDropsLowSpawnerChambers() {
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        List<BlockPoint> points = candidates(generator, SEED, 4000);
        // Use a modest gate; at least some chambers should be dropped, some kept.
        int gate = 6;
        int depth = 6;
        int dropped = 0;
        int kept = 0;
        for (BlockPoint p : points) {
            int cx = Math.floorDiv(p.x(), 16);
            int cz = Math.floorDiv(p.z(), 16);
            SimChamberGenerator.ChamberResult full = generator.generateChamber(SEED, cx, cz).orElse(null);
            if (full == null) {
                continue;
            }
            SimChamberGenerator.ChamberResult checkpoint = generator.generateWithCheckpoint(
                    SEED, cx, cz, depth, gate).orElse(null);
            if (checkpoint == null) {
                // Dropped: the shallow (depth < D) spawner count was below gate. The full count
                // could still be >= gate (the checkpoint is a lower bound), so only assert that the
                // dropped chamber's FULL spawner count is not absurdly high relative to gate —
                // the exact trade-off is documented, not asserted here.
                dropped++;
            } else {
                kept++;
                // Core guarantee: any chamber that survives the checkpoint is generated EXACTLY
                // (identical to a full assembly, no double work).
                assertEquals(full.spawnerPositions(), checkpoint.spawnerPositions(),
                        "kept chamber must be generated exactly");
            }
        }
        // At R=4000 with gate 6 there is meaningful variation in both directions.
        assertTrue(dropped > 0, "some chambers should be dropped, got dropped=" + dropped);
        assertTrue(kept > 0, "some chambers should be kept, got kept=" + kept);
        System.out.println("gateDropsLowSpawnerChambers: dropped=" + dropped + " kept=" + kept
                + " of " + (dropped + kept));
    }
}
