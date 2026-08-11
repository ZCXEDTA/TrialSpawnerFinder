package cn.trialfinder.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link SpawnerConfig} parses the bundled trial-spawner config JSONs.
 */
class SpawnerConfigTest {

    @Test
    void parsesSkeletonNormalConfig() {
        SpawnerConfig.Config cfg = SpawnerConfig.load("minecraft:trial_chamber/ranged/skeleton/normal");
        assertNotNull(cfg, "skeleton normal config must load");
        assertEquals(20, cfg.ticksBetweenSpawn());
        assertEquals(3.0, cfg.simultaneousMobs());
        assertEquals(0.5, cfg.simultaneousMobsPerPlayer());
        assertEquals("minecraft:skeleton", cfg.primaryEntity());
        assertEquals(1, cfg.potentials().get(0).weight());
    }

    @Test
    void parsesBreezeConfig() {
        SpawnerConfig.Config cfg = SpawnerConfig.load("minecraft:trial_chamber/breeze/normal");
        assertNotNull(cfg);
        assertEquals("minecraft:breeze", cfg.primaryEntity());
        assertTrue(cfg.totalMobs() > 0, "breeze config has total_mobs");
        assertEquals(1.0, cfg.totalMobsPerPlayer(), 1e-9);
    }

    @Test
    void poisonSkeletonResolvesToBogged() {
        SpawnerConfig.Config cfg = SpawnerConfig.load("minecraft:trial_chamber/ranged/poison_skeleton/normal");
        assertNotNull(cfg);
        assertEquals("minecraft:bogged", cfg.primaryEntity(),
                "poison_skeleton config spawns bogged mobs");
    }

    @Test
    void unknownConfigReturnsNull() {
        assertNull(SpawnerConfig.load("minecraft:trial_chamber/does_not_exist/normal"));
        assertNull(SpawnerConfig.load(null));
        assertNull(SpawnerConfig.load(""));
    }

    @Test
    void resourcePathMapping() {
        assertEquals("/data/minecraft/trial_spawner/trial_chamber/ranged/skeleton/normal.json",
                SpawnerConfig.toResourcePath("minecraft:trial_chamber/ranged/skeleton/normal"));
    }
}
