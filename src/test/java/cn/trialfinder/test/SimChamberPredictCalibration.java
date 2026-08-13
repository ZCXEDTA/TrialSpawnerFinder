package cn.trialfinder.test;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.sim.SimChamberGenerator;

import java.util.List;

/**
 * Calibration harness for the predict-and-verify checkpoint prefilter. For a few seeds it
 * enumerates candidates, generates each fully, and records — for every checkpoint depth D — the
 * shallow (depth &lt; D) spawner count vs the full count. It then prints a recall table:
 * for each (D, gate), what fraction of chambers whose FULL count >= gate would be kept
 * (i.e. not dropped by the checkpoint). This informs the default {@code --predict-depth} value.
 *
 * <p>Run standalone: {@code ./gradlew runPredictCalibration} (see build.gradle) or from the IDE.
 */
public final class SimChamberPredictCalibration {

    private static final long[] SEEDS = {12345L, 188188L, 42L, 7L, -6523988883445283364L};
    private static final int RADIUS = 6000;
    private static final int[] DEPTHS = {4, 6, 8, 10};

    private SimChamberPredictCalibration() {
    }

    public static void main(String[] args) {
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        cn.trialfinder.accel.CpuAccelerator acc = new cn.trialfinder.accel.CpuAccelerator();

        // Per chamber: (fullCount, shallowCounts[depths]).
        List<int[]> samples = new java.util.ArrayList<>();
        int total = 0;
        for (long seed : SEEDS) {
            List<BlockPoint> points = acc.findChunks(seed, -RADIUS, RADIUS, -RADIUS, RADIUS, true, 0, 0,
                    (long) RADIUS * RADIUS);
            for (BlockPoint p : points) {
                int cx = Math.floorDiv(p.x(), 16);
                int cz = Math.floorDiv(p.z(), 16);
                SimChamberGenerator.ChamberResult full = generator.generateChamber(seed, cx, cz).orElse(null);
                if (full == null) {
                    continue;
                }
                int[] row = new int[1 + DEPTHS.length];
                row[0] = full.spawnerPositions().size();
                for (int d = 0; d < DEPTHS.length; d++) {
                    row[1 + d] = generator.shallowSpawnerCount(seed, cx, cz, DEPTHS[d]);
                }
                samples.add(row);
                total++;
            }
        }

        System.out.printf("Calibration: %d chambers across seeds %s%n", total, java.util.Arrays.toString(SEEDS));
        System.out.printf("%-5s %-8s %-10s %-10s %-10s%n", "D", "gate", "kept", "dropped", "recall");
        for (int depth : DEPTHS) {
            for (int gate : new int[]{4, 8, 12, 20}) {
                int kept = 0;
                int qualifying = 0;
                for (int[] row : samples) {
                    int full = row[0];
                    if (full < gate) {
                        continue;
                    }
                    qualifying++;
                    if (row[1 + indexOf(depth)] >= gate) {
                        kept++;
                    }
                }
                double recall = qualifying == 0 ? 1.0 : (double) kept / qualifying;
                System.out.printf("%-5d %-8d %-10d %-10d %-10.3f%n",
                        depth, gate, kept, qualifying - kept, recall);
            }
            System.out.println();
        }
    }

    private static int indexOf(int depth) {
        for (int i = 0; i < DEPTHS.length; i++) {
            if (DEPTHS[i] == depth) {
                return i;
            }
        }
        throw new IllegalArgumentException("depth " + depth);
    }
}
