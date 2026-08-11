package cn.trialfinder.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disk cache for B-flow chamber generation. Keyed by {@code (seed, chunkX, chunkZ)}; each entry
 * stores the chamber's trial-spawner block positions and their resolved mob types. Repeated
 * searches (or overlapping query points) reuse the cache instead of re-running Jigsaw assembly.
 *
 * <p>Thread-safe: reads are independent, writes go through a temp file + atomic move, and
 * same-key generation is serialized by {@link #lockFor} so a chamber is never assembled twice
 * concurrently.
 *
 * <p>Cache files live at {@code <dir>/spawners_<seed>_<chunkX>_<chunkZ>.json} in the shape
 * {@code {"seed":..,"chunkX":..,"chunkZ":..,"spawners":[{"x":..,"y":..,"z":..,"mob":"skeleton"}]}}.
 */
public final class SpawnerCache {

    /** A single cached spawner: block position plus resolved mob type (e.g. {@code "skeleton"}). */
    public record SpawnerData(int x, int y, int z, String mob) {
    }

    /** JSON file layout — mirrors the on-disk cache file. */
    public static final class CacheFile {
        public long seed;
        public int chunkX;
        public int chunkZ;
        public List<SpawnerData> spawners = new ArrayList<>();
    }

    private final Path dir;
    private final boolean enabled;
    private final boolean debug;
    private final Gson gson = new GsonBuilder().create();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * @param dir     cache directory (created on first write)
     * @param enabled when false all reads miss and writes are no-ops
     * @param debug   when true, cache hits/misses are logged to stdout
     */
    public SpawnerCache(Path dir, boolean enabled, boolean debug) {
        this.dir = dir;
        this.enabled = enabled;
        this.debug = debug;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean debug() {
        return this.debug;
    }

    public Path dir() {
        return this.dir;
    }

    public static String key(long seed, int chunkX, int chunkZ) {
        return seed + "_" + chunkX + "_" + chunkZ;
    }

    /** Per-key monitor so concurrent generation of the same chamber is serialized. */
    public Object lockFor(long seed, int chunkX, int chunkZ) {
        return this.locks.computeIfAbsent(key(seed, chunkX, chunkZ), ignored -> new Object());
    }

    /** Returns the cached spawners for the key, or {@code null} when absent / disabled / corrupt. */
    public List<SpawnerData> get(long seed, int chunkX, int chunkZ) {
        if (!this.enabled) {
            return null;
        }
        Path file = fileFor(seed, chunkX, chunkZ);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CacheFile parsed = this.gson.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), CacheFile.class);
            if (parsed == null || parsed.seed != seed
                    || parsed.chunkX != chunkX || parsed.chunkZ != chunkZ) {
                return null;
            }
            return parsed.spawners;
        } catch (IOException | RuntimeException e) {
            if (this.debug) {
                System.out.println("[DEBUG] cache read failed for " + file + ": " + e.getMessage());
            }
            return null;
        }
    }

    /** Writes the spawners to the cache file (temp file + atomic move). */
    public void put(long seed, int chunkX, int chunkZ, List<SpawnerData> spawners) {
        if (!this.enabled) {
            return;
        }
        try {
            Files.createDirectories(this.dir);
            CacheFile file = new CacheFile();
            file.seed = seed;
            file.chunkX = chunkX;
            file.chunkZ = chunkZ;
            file.spawners = new ArrayList<>(spawners);
            Path target = fileFor(seed, chunkX, chunkZ);
            Path tmp = this.dir.resolve(target.getFileName() + ".tmp");
            Files.writeString(tmp, this.gson.toJson(file), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (this.debug) {
                System.out.println("[DEBUG] cache write failed: " + e.getMessage());
            }
        }
    }

    public Path fileFor(long seed, int chunkX, int chunkZ) {
        return this.dir.resolve("spawners_" + key(seed, chunkX, chunkZ) + ".json");
    }
}
