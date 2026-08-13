package cn.trialfinder.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerCacheTest {

    @TempDir
    Path tempDir;

    private static List<SpawnerCache.SpawnerData> sampleSpawners() {
        return List.of(
                new SpawnerCache.SpawnerData(-432, -38, 215, "stray"),
                new SpawnerCache.SpawnerData(-438, -38, 220, "breeze"));
    }

    private static List<SpawnerCache.VaultData> sampleVaults() {
        return List.of(
                new SpawnerCache.VaultData(-430, -36, 218, false),
                new SpawnerCache.VaultData(-440, -36, 222, true));
    }

    @Test
    void putThenGetRoundTrips() {
        SpawnerCache cache = new SpawnerCache(tempDir, true, false);
        assertNull(cache.get(188188L, 12, 34), "empty cache must miss");

        cache.put(188188L, 12, 34, sampleSpawners(), sampleVaults());

        SpawnerCache.CachedChamber loaded = cache.get(188188L, 12, 34);
        assertNotNull(loaded, "value written to cache must be readable");
        assertEquals(sampleSpawners(), loaded.spawners());
        assertEquals(2, loaded.spawners().size());
        assertEquals("stray", loaded.spawners().get(0).mob());
        assertEquals(sampleVaults(), loaded.vaults());
        assertEquals(2, loaded.vaults().size());
        assertTrue(loaded.vaults().get(1).ominous());

        // Different key is unaffected.
        assertNull(cache.get(188188L, 12, 35));
    }

    @Test
    void allChambersInOneSeedShareOneFile() throws Exception {
        SpawnerCache cache = new SpawnerCache(tempDir, true, false);
        int chunkA = 12, chunkB = 400;
        assertEquals(cache.fileFor(188188L), cache.fileFor(188188L),
                "both chunks must map to the same seed file");

        cache.put(188188L, chunkA, chunkA, sampleSpawners(), sampleVaults());
        cache.put(188188L, chunkB, chunkB, sampleSpawners(), sampleVaults());
        cache.flush(188188L);

        assertEquals(1, Files.list(tempDir).filter(p -> p.getFileName().toString().startsWith("spawners_")).count(),
                "both chambers must live in one file");
        Path file = cache.fileFor(188188L);
        assertTrue(Files.isRegularFile(file), "file must exist after flush: " + file);
    }

    @Test
    void flushThenReopenReadsPersistedChambers() throws Exception {
        SpawnerCache cache = new SpawnerCache(tempDir, true, false);
        cache.put(188188L, 12, 34, sampleSpawners(), sampleVaults());
        cache.flush(188188L);

        // A fresh instance (same dir) must read the persisted single file.
        SpawnerCache reopened = new SpawnerCache(tempDir, true, false);
        SpawnerCache.CachedChamber loaded = reopened.get(188188L, 12, 34);
        assertNotNull(loaded, "persisted chamber must be readable after reopen");
        assertEquals(sampleSpawners(), loaded.spawners());
        assertEquals(sampleVaults(), loaded.vaults());
    }

    @Test
    void oldCacheFileWithoutVaultsReadsAsEmptyVaults() throws Exception {
        SpawnerCache cache = new SpawnerCache(tempDir, true, false);
        cache.put(188188L, 12, 34, sampleSpawners(), null);
        cache.flush(188188L);
        SpawnerCache.CachedChamber loaded = cache.get(188188L, 12, 34);
        assertNotNull(loaded);
        assertEquals(2, loaded.spawners().size());
        assertTrue(loaded.vaults().isEmpty(), "vaults must be empty when none were written");
    }

    @Test
    void disabledCacheNeverReadsOrWrites() throws Exception {
        SpawnerCache cache = new SpawnerCache(tempDir, false, false);
        cache.put(1L, 1, 1, sampleSpawners(), sampleVaults());
        assertNull(cache.get(1L, 1, 1));
        cache.flush(1L);
        assertFalse(cache.isEnabled());
        assertFalse(Files.exists(tempDir.resolve("spawners_1.bin")),
                "disabled cache must not write a file");
    }

    @Test
    void corruptFileMisses() throws Exception {
        SpawnerCache cache = new SpawnerCache(tempDir, true, false);
        Path file = cache.fileFor(7L);
        Files.createDirectories(tempDir);
        Files.writeString(file, "not binary {");
        assertNull(cache.get(7L, 8, 9), "corrupt cache file must be treated as a miss");
    }

    @Test
    void mismatchedKeyInFileMisses() throws Exception {
        SpawnerCache cache = new SpawnerCache(tempDir, true, false);
        cache.put(1L, 2, 3, sampleSpawners(), sampleVaults());
        cache.flush(1L);
        // Different seed reads its own (absent) file.
        assertNull(cache.get(2L, 2, 3));
        // Same seed, different chunk is absent.
        assertNull(cache.get(1L, 99, 3));
    }

    @Test
    void keyFormat() {
        assertEquals("188188_12_34", SpawnerCache.key(188188L, 12, 34));
        assertFalse(SpawnerCache.key(188188L, -12, 34).contains("  "));
    }
}
