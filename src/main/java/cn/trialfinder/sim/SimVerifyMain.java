package cn.trialfinder.sim;

import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.ChunkPos;
import cn.trialfinder.sim.math.Mth;
import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.random.WorldgenRandom;
import cn.trialfinder.sim.structure.pools.PoolElementStructurePiece;
import cn.trialfinder.sim.structure.pools.alias.PoolAliasLookup;
import cn.trialfinder.sim.structure.placement.RandomSpreadStructurePlacement;
import cn.trialfinder.sim.structure.placement.RandomSpreadType;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Standalone verification driver (the requested test main class). Exercises A, B and C flows
 * with the same assertions as the JUnit tests, and prints a PASS/FAIL summary. No JUnit needed.
 *
 * <p>Usage: java cn.trialfinder.sim.SimVerifyMain [seed]
 */
public final class SimVerifyMain {
    private SimVerifyMain() {
    }

    private static int failures = 0;

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        check("RNG bit-exact vs java.util.Random", SimVerifyMain::checkRng);
        check("A flow matches reference candidate", SimVerifyMain::checkAFlow);
        check("C flow alias resolution matches java.util.Random", SimVerifyMain::checkCFlow);
        check("B flow full chamber assembly", () -> checkBFlow(seed));
        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void check(String name, Runnable body) {
        try {
            body.run();
            System.out.println("PASS: " + name);
        } catch (AssertionError | RuntimeException e) {
            failures++;
            System.out.println("FAIL: " + name + " — " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    // ------------------------------------------------------------ RNG

    private static void checkRng() {
        for (long s : new long[]{0L, 1L, 12345L, -1L, 9206294873968313284L}) {
            Random jdk = new Random(s);
            RandomSource sim = new LegacyRandomSource(s);
            for (int bound : new int[]{1, 2, 3, 22, 34, 100, 4096}) {
                for (int i = 0; i < 30; i++) {
                    assertEquals(jdk.nextInt(bound), sim.nextInt(bound), "nextInt(" + bound + ") seed " + s);
                }
            }
            for (int i = 0; i < 30; i++) {
                assertEquals(jdk.nextLong(), sim.nextLong(), "nextLong seed " + s);
            }
        }
    }

    // ------------------------------------------------------------ A flow

    private static void checkAFlow() {
        RandomSpreadStructurePlacement placement =
                new RandomSpreadStructurePlacement(34, 12, RandomSpreadType.LINEAR, 94_251_327);
        // Constants from structure_set/trial_chambers.json.
        assertEquals(34, placement.spacing(), "spacing");
        assertEquals(12, placement.separation(), "separation");
        assertEquals(94_251_327, placement.salt(), "salt");

        // Independent reference (report §5.1): setLargeFeatureWithSalt + 2x nextInt(22), via
        // java.util.Random which shares the exact LCG.
        for (long seed : new long[]{0L, 1L, 12345L, -1L, 9206294873968313284L}) {
            for (int regionX : new int[]{-3, 0, 1, 34, -34, 100}) {
                for (int regionZ : new int[]{-2, 0, 7, 17, -17}) {
                    ChunkPos mine = placement.getPotentialStructureChunkFromRegion(seed, regionX, regionZ);
                    long m = (long) regionX * 341_873_128_712L + (long) regionZ * 132_897_987_541L + seed + 94_251_327L;
                    Random jdk = new Random(m);
                    int expectedChunkX = regionX * 34 + jdk.nextInt(22);
                    int expectedChunkZ = regionZ * 34 + jdk.nextInt(22);
                    assertEquals(expectedChunkX, mine.x(), "chunk X region " + regionX + "," + regionZ);
                    assertEquals(expectedChunkZ, mine.z(), "chunk Z region " + regionX + "," + regionZ);
                }
            }
        }
    }

    // ------------------------------------------------------------ C flow

    private static final String[] RANGED = {"skeleton", "stray", "poison_skeleton"};
    private static final String[] MELEE = {"zombie", "husk", "spider"};
    private static final String[] SMALL = {"slime", "cave_spider", "silverfish", "baby_zombie"};

    private static void checkCFlow() {
        long worldSeed = 12345L;
        for (int chunkX : new int[]{-124, 0, 7, 84}) {
            for (int chunkZ : new int[]{8, 14, -93, 47}) {
                BlockPos startPos = new BlockPos(chunkX * 16, -20, chunkZ * 16);
                RandomSource base = new LegacyRandomSource(worldSeed);
                long positionalSeed = base.nextLong();
                long atSeed = Mth.getSeed(startPos.getX(), startPos.getY(), startPos.getZ()) ^ positionalSeed;
                Random ref = new Random(atSeed);
                int group = ref.nextInt(3);
                int melee = ref.nextInt(3);
                int small = ref.nextInt(4);

                PoolAliasLookup lookup = PoolAliasLookup.create(TrialChambersData.ALIAS_BINDINGS, startPos, worldSeed);
                assertEquals("ranged/" + RANGED[group], path(lookup, "contents/ranged"), "ranged");
                assertEquals("slow_ranged/" + RANGED[group], path(lookup, "contents/slow_ranged"), "slow_ranged");
                assertEquals("melee/" + MELEE[melee], path(lookup, "contents/melee"), "melee");
                assertEquals("small_melee/" + SMALL[small], path(lookup, "contents/small_melee"), "small_melee");
            }
        }
    }

    private static String path(PoolAliasLookup lookup, String suffix) {
        return lookup.lookup(TrialChambersData.spawnerKey(suffix)).identifier().getPath()
                .substring("trial_chambers/spawner/".length());
    }

    // ------------------------------------------------------------ B flow

    private static void checkBFlow(long seed) {
        SimChamberGenerator generator = new SimChamberGenerator(Path.of("src/main/resources"));
        List<ChunkPos> candidates = generator.enumeratePotentialChunks(seed, -2000, 2000, -2000, 2000);
        assertTrue(candidates.size() == 56, "candidate count for seed " + seed + " = " + candidates.size());

        int ok = 0;
        int totalSpawners = 0;
        for (ChunkPos chunk : candidates) {
            SimChamberGenerator.ChamberResult result = generator.generate(seed, chunk.x(), chunk.z()).orElse(null);
            if (result == null) {
                continue;
            }
            ok++;
            totalSpawners += result.spawnerPositions().size();
            assertTrue(result.assembly().pieces().size() > 10, "chamber should have many pieces");
            assertTrue(result.mobAliases().ranged().endsWith("/skeleton")
                    || result.mobAliases().ranged().endsWith("/stray")
                    || result.mobAliases().ranged().endsWith("/poison_skeleton"), "mob alias");
            // Spawner positions must be unique (the generator never double-places a spawner).
            List<cn.trialfinder.sim.math.BlockPos> spawners = result.spawnerPositions();
            assertTrue(new java.util.HashSet<>(spawners).size() == spawners.size(),
                    "spawner positions must be unique, got " + spawners.size());
        }
        System.out.println("  B flow detail: candidates=" + candidates.size() + " generated=" + ok
                + " totalSpawners=" + totalSpawners);
        assertTrue(ok > 40, "most candidates should generate a chamber");
    }
}
