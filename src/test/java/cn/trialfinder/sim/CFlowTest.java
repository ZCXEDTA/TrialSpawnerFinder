package cn.trialfinder.sim;

import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Mth;
import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.structure.pools.alias.PoolAliasLookup;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C flow verification: the pool-alias resolution (mob types) must match an independent
 * computation using java.util.Random (identical LCG), for the same positional seed derivation.
 *
 * <p>Note: the analysis report claimed C flow uses Xoroshiro — this is incorrect. The compiled
 * 1.21.11 code uses {@code RandomSource.create(seed)} = LegacyRandomSource, so the alias stream
 * is the classic LCG seeded by {@code Mth.getSeed(startPos) ^ forkPositional().nextLong()}.
 */
class CFlowTest {

    private static final String[] RANGED_MOBS = {"skeleton", "stray", "poison_skeleton"};
    private static final String[] MELEE_MOBS = {"zombie", "husk", "spider"};
    private static final String[] SMALL_MELEE_MOBS = {"slime", "cave_spider", "silverfish", "baby_zombie"};

    @Test
    void aliasResolutionMatchesJavaUtilRandom() {
        long worldSeed = 12345L;
        for (int chunkX : new int[]{-124, 0, 7, 84, -23}) {
            for (int chunkZ : new int[]{8, 14, -93, 47, 110}) {
                for (int y : new int[]{-40, -20, -25}) {
                    BlockPos startPos = new BlockPos(chunkX * 16, y, chunkZ * 16);

                    // Independent reference: replicate RandomSource.create(seed).forkPositional().at(pos).
                    RandomSource base = new LegacyRandomSource(worldSeed);
                    long positionalSeed = base.nextLong();
                    long atSeed = Mth.getSeed(startPos.getX(), startPos.getY(), startPos.getZ()) ^ positionalSeed;
                    Random ref = new Random(atSeed);
                    int group = ref.nextInt(3);
                    int melee = ref.nextInt(3);
                    int small = ref.nextInt(4);

                    PoolAliasLookup lookup = PoolAliasLookup.create(TrialChambersData.ALIAS_BINDINGS, startPos, worldSeed);
                    String ranged = path(lookup, "contents/ranged");
                    String slowRanged = path(lookup, "contents/slow_ranged");
                    String meleePath = path(lookup, "contents/melee");
                    String smallMelee = path(lookup, "contents/small_melee");

                    assertEquals("ranged/" + RANGED_MOBS[group], ranged, "ranged at " + startPos);
                    assertEquals("slow_ranged/" + RANGED_MOBS[group], slowRanged, "slow_ranged at " + startPos);
                    assertEquals("melee/" + MELEE_MOBS[melee], meleePath, "melee at " + startPos);
                    assertEquals("small_melee/" + SMALL_MELEE_MOBS[small], smallMelee, "small_melee at " + startPos);
                }
            }
        }
    }

    @Test
    void aliasLookupIsIdentityForUnknownKeys() {
        PoolAliasLookup lookup = PoolAliasLookup.create(TrialChambersData.ALIAS_BINDINGS,
                new BlockPos(0, -20, 0), 12345L);
        cn.trialfinder.sim.resources.ResourceKey<cn.trialfinder.sim.structure.pools.StructureTemplatePool> key =
                cn.trialfinder.sim.resources.ResourceKey.create("trial_chambers/chamber/addon");
        assertEquals(key, lookup.lookup(key));
    }

    private static String path(PoolAliasLookup lookup, String suffix) {
        String full = lookup.lookup(TrialChambersData.spawnerKey(suffix)).identifier().getPath();
        assertTrue(full.startsWith("trial_chambers/spawner/"));
        return full.substring("trial_chambers/spawner/".length());
    }
}
