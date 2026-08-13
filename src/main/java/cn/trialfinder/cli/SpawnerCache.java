package cn.trialfinder.cli;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
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
 * <p>All chambers for one seed live in a single compact binary file
 * {@code spawners_<seed>.bin}, so a large search that generates tens of thousands of chambers
 * produces exactly one file. The on-disk format is a {@link DataOutputStream} stream (magic +
 * version + seed + chamber list), written atomically (temp file + move) on {@link #flush}.
 *
 * <p>Thread-safe: {@link #get} and {@link #put} operate on an in-memory map; {@link #put} marks
 * the entry dirty and triggers a {@link #flush} every {@link #FLUSH_INTERVAL} new chambers (plus
 * an explicit final {@link #flush} from the caller), so an interrupted scan resumes from the last
 * flushed state. {@link #lockFor} serializes generation of the same chamber.
 */
public final class SpawnerCache {

    private static final int MAGIC = 0x53504331;      // "SPC1"
    private static final int FORMAT_VERSION = 1;
    /** Flush the file after this many new chambers (bounds loss on an interrupted scan). */
    public static final int FLUSH_INTERVAL = 5000;

    /**
     * A single cached spawner: block position plus resolved mob type (e.g. {@code "skeleton"})
     * and the trial-spawner config id (e.g. {@code "minecraft:trial_chamber/ranged/skeleton/normal"}).
     * {@code config} may be {@code null} for cache files written by older builds.
     */
    public record SpawnerData(int x, int y, int z, String mob, String config) {
        public SpawnerData(int x, int y, int z, String mob) {
            this(x, y, z, mob, null);
        }
    }

    /** A single cached vault block: its position and whether it is the ominous variant. */
    public record VaultData(int x, int y, int z, boolean ominous) {
    }

    /** A full cached chamber: spawners plus vaults. */
    public record CachedChamber(List<SpawnerData> spawners, List<VaultData> vaults) {
    }

    private final Path dir;
    private final boolean enabled;
    private final boolean debug;
    /** In-memory cache: seed -> (chunkKey -> chamber). */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, CachedChamber>> bySeed =
            new ConcurrentHashMap<>();
    /** Per-(seed,chunk) monitor so the same chamber is never assembled twice concurrently. */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private final java.util.concurrent.atomic.AtomicInteger newSinceFlush =
            new java.util.concurrent.atomic.AtomicInteger();

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

    private static String chunkKey(int chunkX, int chunkZ) {
        return chunkX + "_" + chunkZ;
    }

    /** Per-chamber monitor so concurrent generation of the same chamber is serialized. */
    public Object lockFor(long seed, int chunkX, int chunkZ) {
        return this.locks.computeIfAbsent(key(seed, chunkX, chunkZ), ignored -> new Object());
    }

    /** Loads the seed's file into the in-memory map once (lazy, on first access). */
    private ConcurrentHashMap<String, CachedChamber> ensureLoaded(long seed) {
        return this.bySeed.computeIfAbsent(seed, s -> {
            ConcurrentHashMap<String, CachedChamber> map = new ConcurrentHashMap<>();
            Path file = fileFor(s);
            if (Files.isRegularFile(file)) {
                try (DataInputStream in = new DataInputStream(
                        new BufferedInputStream(Files.newInputStream(file)))) {
                    if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) {
                        return map; // unsupported/corrupt — treat as empty
                    }
                    long fileSeed = in.readLong();
                    if (fileSeed != s) {
                        return map;
                    }
                    int count = in.readInt();
                    if (count < 0 || count > 100_000_000) {
                        return map;
                    }
                    for (int i = 0; i < count; i++) {
                        int chunkX = in.readInt();
                        int chunkZ = in.readInt();
                        List<SpawnerData> spawners = new ArrayList<>();
                        int spawnerCount = in.readInt();
                        for (int j = 0; j < spawnerCount; j++) {
                            int x = in.readInt();
                            int y = in.readInt();
                            int z = in.readInt();
                            String mob = in.readUTF();
                            String config = in.readUTF();
                            spawners.add(new SpawnerData(x, y, z, mob, config.isEmpty() ? null : config));
                        }
                        List<VaultData> vaults = new ArrayList<>();
                        int vaultCount = in.readInt();
                        for (int j = 0; j < vaultCount; j++) {
                            int x = in.readInt();
                            int y = in.readInt();
                            int z = in.readInt();
                            boolean ominous = in.readBoolean();
                            vaults.add(new VaultData(x, y, z, ominous));
                        }
                        map.put(chunkKey(chunkX, chunkZ), new CachedChamber(spawners, vaults));
                    }
                } catch (IOException e) {
                    if (this.debug) {
                        System.out.println("[DEBUG] cache read failed for " + file + ": " + e.getMessage());
                    }
                }
            }
            return map;
        });
    }

    /** Returns the cached chamber for the key, or {@code null} when absent / disabled / corrupt. */
    public CachedChamber get(long seed, int chunkX, int chunkZ) {
        if (!this.enabled) {
            return null;
        }
        return ensureLoaded(seed).get(chunkKey(chunkX, chunkZ));
    }

    /** Stores the chamber in the in-memory map; flushes the file every {@link #FLUSH_INTERVAL} new entries. */
    public void put(long seed, int chunkX, int chunkZ, List<SpawnerData> spawners, List<VaultData> vaults) {
        if (!this.enabled) {
            return;
        }
        ConcurrentHashMap<String, CachedChamber> map = ensureLoaded(seed);
        map.put(chunkKey(chunkX, chunkZ), new CachedChamber(
                List.copyOf(spawners),
                vaults != null ? List.copyOf(vaults) : List.of()));
        if (this.newSinceFlush.incrementAndGet() >= FLUSH_INTERVAL) {
            flush(seed);
            this.newSinceFlush.set(0);
        }
    }

    /** Writes the seed's full chamber set to its file atomically. */
    public void flush(long seed) {
        if (!this.enabled) {
            return;
        }
        ConcurrentHashMap<String, CachedChamber> map = this.bySeed.get(seed);
        if (map == null) {
            return;
        }
        synchronized (this.writeLock) {
            try {
                Files.createDirectories(this.dir);
                Path target = fileFor(seed);
                Path tmp = this.dir.resolve(target.getFileName() + ".tmp");
                try (DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                    out.writeInt(MAGIC);
                    out.writeInt(FORMAT_VERSION);
                    out.writeLong(seed);
                    out.writeInt(map.size());
                    // Deterministic order for a stable file (not required, but reproducible).
                    List<ConcurrentHashMap.Entry<String, CachedChamber>> entries =
                            new ArrayList<>(map.entrySet());
                    entries.sort(java.util.Map.Entry.comparingByKey());
                    for (ConcurrentHashMap.Entry<String, CachedChamber> entry : entries) {
                        String[] parts = entry.getKey().split("_");
                        int chunkX = Integer.parseInt(parts[0]);
                        int chunkZ = Integer.parseInt(parts[1]);
                        writeChamber(out, chunkX, chunkZ, entry.getValue());
                    }
                }
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                if (this.debug) {
                    System.out.println("[DEBUG] cache write failed: " + e.getMessage());
                }
            }
        }
    }

    /** Flushes every seed that has been touched in this JVM. */
    public void flush() {
        if (!this.enabled) {
            return;
        }
        for (Long seed : this.bySeed.keySet()) {
            flush(seed);
        }
        this.newSinceFlush.set(0);
    }

    private static void writeChamber(DataOutputStream out, int chunkX, int chunkZ,
                                     CachedChamber chamber) throws IOException {
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        out.writeInt(chamber.spawners().size());
        for (SpawnerData s : chamber.spawners()) {
            out.writeInt(s.x());
            out.writeInt(s.y());
            out.writeInt(s.z());
            out.writeUTF(s.mob());
            out.writeUTF(s.config() != null ? s.config() : "");
        }
        out.writeInt(chamber.vaults().size());
        for (VaultData v : chamber.vaults()) {
            out.writeInt(v.x());
            out.writeInt(v.y());
            out.writeInt(v.z());
            out.writeBoolean(v.ominous());
        }
    }

    public Path fileFor(long seed) {
        return this.dir.resolve("spawners_" + seed + ".bin");
    }
}
