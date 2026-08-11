package cn.trialfinder.test;

import cn.trialfinder.sim.SimChamberGenerator;
import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.ChunkPos;
import cn.trialfinder.sim.resources.ClasspathResourceLoader;
import cn.trialfinder.sim.structure.pools.PoolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone verification that {@code cn.trialfinder.sim.*} runs without a Minecraft server.
 *
 * <p>Loads every resource (pool JSONs + template NBTs) from the classpath, generates a chamber
 * for seed 12345 / region (0,0), and prints the resource counts and result. Usable both as a
 * JUnit test ({@code ./gradlew test}) and as a runnable main
 * ({@code ./gradlew runStandalone} or {@code java cn.trialfinder.test.TestStandalone}).
 */
public final class TestStandalone {

    public static void main(String[] args) {
        try {
            long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
            Report report = run(seed);
            report.print();
            if (!report.ok) {
                System.exit(1);
            }
        } catch (Throwable t) {
            System.err.println("STANDALONE FAILED: " + t);
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    @Test
    void generatesChamberFromClasspath() {
        Report report = run(12345L);
        assertTrue(report.ok, "standalone run failed: " + report.error);
        assertTrue(report.poolFiles >= 47, "pool JSON files " + report.poolFiles + " < 47");
        assertTrue(report.templateFiles >= 191, "template NBT files " + report.templateFiles + " < 191");
        assertTrue(report.poolCount >= 47, "registered pools " + report.poolCount + " < 47");
        assertTrue(report.pieceCount > 0, "no pieces generated");
        assertTrue(report.spawnerCount > 0, "no spawners found");
    }

    /** Executes the standalone pipeline and collects the outcome (no System.exit). */
    public static Report run(long seed) {
        Report report = new Report();
        try {
            // 1. Classpath data root must be enumerable.
            Path dataRoot = ClasspathResourceLoader.dataRootPath();
            if (dataRoot == null) {
                report.error = "classpath data directory is not unpacked on disk (running from a jar?)";
                return report;
            }

            // 2. Count resources on the classpath.
            Path poolRoot = dataRoot.resolve("minecraft").resolve("worldgen/template_pool");
            Path structureRoot = dataRoot.resolve("minecraft").resolve("structure");
            report.poolFiles = ClasspathResourceLoader.countFiles(poolRoot, ".json");
            report.templateFiles = ClasspathResourceLoader.countFiles(structureRoot, ".nbt");

            // 3. Build a fully self-contained generator from the classpath.
            SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
            report.poolCount = generator.pools().pools().size();

            // 4. Resolve the potential chunk for region (0,0) (A flow).
            ChunkPos chunk = TrialChambersData.PLACEMENT.getPotentialStructureChunkFromRegion(seed, 0, 0);
            report.chunk = chunk;

            // 5. Assemble the chamber (B flow + C flow).
            SimChamberGenerator.ChamberResult result = generator.generateChamber(seed, chunk.x(), chunk.z()).orElse(null);
            if (result == null) {
                report.error = "chamber generation returned empty (rejected by height limits?) at chunk " + chunk;
                return report;
            }
            report.pieceCount = result.assembly().pieces().size();
            List<BlockPos> spawners = result.spawnerPositions();
            report.spawnerCount = spawners.size();
            report.sampleSpawners = spawners.stream().limit(5).toList();
            report.mobAliases = result.mobAliases().toString();
            report.ok = true;
        } catch (Throwable t) {
            report.error = t.toString();
            report.stackTrace = t;
        }
        return report;
    }

    /** Immutable-ish result holder. */
    public static final class Report {
        public boolean ok = false;
        public long poolFiles = 0;
        public long templateFiles = 0;
        public int poolCount = 0;
        public int pieceCount = 0;
        public int spawnerCount = 0;
        public ChunkPos chunk = null;
        public List<BlockPos> sampleSpawners = List.of();
        public String mobAliases = "";
        public String error = "";
        public Throwable stackTrace = null;

        public void print() {
            System.out.println("=== TestStandalone report (classpath-only) ===");
            System.out.println("data root dir : " + (ClasspathResourceLoader.dataRootPath() != null
                    ? ClasspathResourceLoader.dataRootPath() : "(jar, not enumerable)"));
            System.out.println("pool JSON files  : " + poolFiles);
            System.out.println("template NBT files: " + templateFiles);
            System.out.println("registered pools : " + poolCount);
            System.out.println("chunk (region 0,0, seed " + 12345L + "): " + chunk);
            System.out.println("pieces          : " + pieceCount);
            System.out.println("spawners        : " + spawnerCount);
            System.out.println("sample spawners : " + sampleSpawners);
            System.out.println("mob aliases     : " + mobAliases);
            System.out.println("result          : " + (ok ? "OK" : "FAILED"));
            if (!ok) {
                System.out.println("error           : " + error);
                if (stackTrace != null) {
                    stackTrace.printStackTrace(System.out);
                }
            }
        }
    }
}
