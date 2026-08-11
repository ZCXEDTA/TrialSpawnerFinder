package cn.trialfinder.sim;

import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.ChunkPos;
import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.WorldgenRandom;
import cn.trialfinder.cli.SpawnerCache;
import cn.trialfinder.sim.nbt.NbtTag;
import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.GenerationContext;
import cn.trialfinder.sim.structure.LiquidSettings;
import cn.trialfinder.sim.structure.StructureBlockInfo;
import cn.trialfinder.sim.structure.StructurePlaceSettings;
import cn.trialfinder.sim.structure.StructureTemplate;
import cn.trialfinder.sim.structure.StructureTemplateManager;
import cn.trialfinder.sim.structure.pools.DimensionPadding;
import cn.trialfinder.sim.structure.pools.JigsawPlacement;
import cn.trialfinder.sim.structure.pools.MaxDistance;
import cn.trialfinder.sim.structure.pools.PoolElementStructurePiece;
import cn.trialfinder.sim.structure.pools.PoolRegistry;
import cn.trialfinder.sim.structure.pools.Projection;
import cn.trialfinder.sim.structure.pools.SinglePoolElement;
import cn.trialfinder.sim.structure.pools.StructurePoolElement;
import cn.trialfinder.sim.structure.pools.StructureTemplatePool;
import cn.trialfinder.sim.structure.pools.alias.PoolAliasLookup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Self-contained simulator for Minecraft 1.21.11 trial-chamber generation. Chains the three
 * random streams:
 * <ul>
 *   <li>A flow — {@link TrialChambersData#PLACEMENT} 34×34 grid placement (which chunk holds a chamber);</li>
 *   <li>B flow — {@link JigsawPlacement} structure-internal jigsaw assembly (piece layout, spawner positions);</li>
 *   <li>C flow — {@link PoolAliasLookup} mob-type alias resolution (ranged/melee/small_melee contents).</li>
 * </ul>
 * No Minecraft server classes are involved.
 */
public final class SimChamberGenerator {
    public static final int WORLD_MIN_Y = -64;
    public static final int WORLD_HEIGHT = 384;

    private final StructureTemplateManager templateManager;
    private final PoolRegistry pools;
    private final GenerationContext.HeightAccessor heightAccessor;
    private SpawnerCache cache;
    /** Jigsaw assembly max depth; {@code <= 0} uses the vanilla {@link TrialChambersData#SIZE}. */
    private int jigsawDepth;

    /** Sets a shallow-jigsaw depth limit (a positive value truncates decorative recursion; spawner
     * counts may drop). {@code <= 0} restores the vanilla depth. */
    public void setJigsawDepth(int jigsawDepth) {
        this.jigsawDepth = jigsawDepth;
    }

    public SimChamberGenerator(Path dataDir) {
        this.templateManager = new StructureTemplateManager(dataDir);
        this.pools = new PoolRegistry(dataDir, this.templateManager);
        this.pools.loadAll();
        this.heightAccessor = new GenerationContext.HeightAccessor(WORLD_MIN_Y, WORLD_HEIGHT);
    }

    /**
     * Constructs a generator over pre-loaded components (e.g. a {@link PoolRegistry} that was
     * loaded purely from the classpath via {@link PoolRegistry#loadFromClasspath()}).
     */
    public SimChamberGenerator(StructureTemplateManager templateManager, PoolRegistry pools) {
        this.templateManager = templateManager;
        this.pools = pools;
        this.heightAccessor = new GenerationContext.HeightAccessor(WORLD_MIN_Y, WORLD_HEIGHT);
    }

    /** Injects (or clears) the B-flow cache used by {@link #generate}. */
    public void setCache(SpawnerCache cache) {
        this.cache = cache;
    }

    /**
     * Builds a fully self-contained generator that loads every resource (pool JSONs and template
     * NBTs) from the classpath, not from a Minecraft resource manager. Throws if the classpath
     * data directory is not enumerable (e.g. running from a jar without unpacked resources).
     */
    public static SimChamberGenerator fromClasspath() {
        long t0 = System.nanoTime();
        Path base = cn.trialfinder.sim.resources.ClasspathResourceLoader.baseDirPath();
        StructureTemplateManager templateManager = new StructureTemplateManager(base);
        PoolRegistry pools = new PoolRegistry(base, templateManager);
        pools.loadFromClasspath();
        System.out.printf("[timing] resource load (pools JSON)     %.1f ms%n",
                (System.nanoTime() - t0) / 1e6);
        SimChamberGenerator generator = new SimChamberGenerator(templateManager, pools);
        generator.preloadTemplates();
        return generator;
    }

    /**
     * Preloads every template referenced by the pool registry into the template manager cache and
     * precomputes each template's per-rotation jigsaw transform and bounding box. This moves all
     * NBT I/O + parsing + rotation computation to initialization time (single-threaded), so
     * parallel B-flow generation never races on a first-use
     * {@code ConcurrentHashMap.computeIfAbsent} load — the main scalability bottleneck once
     * single-chamber CPU cost is low.
     */
    private void preloadTemplates() {
        long t0 = System.nanoTime();
        for (cn.trialfinder.sim.structure.pools.StructureTemplatePool pool
                : this.pools.pools().values()) {
            for (cn.trialfinder.sim.util.Pair<StructurePoolElement, Integer> entry : pool.getTemplates()) {
                entry.first().collectTemplateIds(this::preloadTemplate);
            }
        }
        System.out.printf("[timing] template preload (NBT)           %.1f ms%n",
                (System.nanoTime() - t0) / 1e6);
    }

    private void preloadTemplate(cn.trialfinder.sim.resources.Identifier id) {
        // Load the NBT template into the manager cache (single-threaded I/O), but do NOT precompute
        // per-rotation jigsaw/bbox transforms here: pre-filling those caches empirically slowed
        // parallel B-flow (the cached lists get shallow-copied per call regardless, and the added
        // pre-fill allocation/garbage outweighed the computeIfAbsent-miss savings).
        this.templateManager.get(id);
    }

    public StructureTemplateManager templateManager() {
        return this.templateManager;
    }

    public PoolRegistry pools() {
        return this.pools;
    }

    // ---------------------------------------------------------------- A flow

    /**
     * Returns the potential structure chunk for every 34×34 region in the given block bounds.
     * Stateless per region — this is the CUDA-parallelizable kernel boundary.
     */
    public List<ChunkPos> enumeratePotentialChunks(long worldSeed, long minX, long maxX, long minZ, long maxZ) {
        int minChunkX = Math.floorDiv((int) minX, 16);
        int maxChunkX = Math.floorDiv((int) maxX, 16);
        int minChunkZ = Math.floorDiv((int) minZ, 16);
        int maxChunkZ = Math.floorDiv((int) maxZ, 16);
        int minRegionX = Math.floorDiv(minChunkX, TrialChambersData.SPACING_CHUNKS) - 1;
        int maxRegionX = Math.floorDiv(maxChunkX, TrialChambersData.SPACING_CHUNKS) + 1;
        int minRegionZ = Math.floorDiv(minChunkZ, TrialChambersData.SPACING_CHUNKS) - 1;
        int maxRegionZ = Math.floorDiv(maxChunkZ, TrialChambersData.SPACING_CHUNKS) + 1;

        List<ChunkPos> result = new ArrayList<>();
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                ChunkPos potential = TrialChambersData.PLACEMENT.getPotentialStructureChunkFromRegion(worldSeed, regionX, regionZ);
                int blockX = potential.getMinBlockX();
                int blockZ = potential.getMinBlockZ();
                if (blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ) {
                    result.add(potential);
                }
            }
        }
        result.sort((a, b) -> {
            int byX = Integer.compare(a.x(), b.x());
            return byX != 0 ? byX : Integer.compare(a.z(), b.z());
        });
        return result;
    }

    // ------------------------------------------------------------ B flow

    /** Convenience alias for {@link #generate}. */
    public java.util.Optional<ChamberResult> generateChamber(long worldSeed, int chunkX, int chunkZ) {
        return generate(worldSeed, chunkX, chunkZ);
    }

    /**
     * Fully assembles the chamber at the given potential structure chunk and returns the result,
     * or empty if the chamber is rejected (too close to world height limits).
     *
     * <p>When a {@link SpawnerCache} is injected and enabled, the result is first looked up in the
     * cache (keyed by seed + chunk). On a hit the cached spawner positions and mob types are
     * returned without re-running Jigsaw assembly; on a miss the chamber is assembled and the
     * result written back. Same-key requests are serialized so a chamber is never assembled twice
     * concurrently.
     */
    public java.util.Optional<ChamberResult> generate(long worldSeed, int chunkX, int chunkZ) {
        if (this.cache != null && this.cache.isEnabled()) {
            synchronized (this.cache.lockFor(worldSeed, chunkX, chunkZ)) {
                List<SpawnerCache.SpawnerData> cached = this.cache.get(worldSeed, chunkX, chunkZ);
                if (cached != null) {
                    if (this.cache.debug()) {
                        System.out.printf("[DEBUG] cache hit  seed=%d chunk=(%d,%d) spawners=%d%n",
                                worldSeed, chunkX, chunkZ, cached.size());
                    }
                    return java.util.Optional.of(ChamberResult.fromCached(cached));
                }
                java.util.Optional<ChamberResult> result = doGenerate(worldSeed, chunkX, chunkZ);
                result.ifPresent(r -> this.cache.put(worldSeed, chunkX, chunkZ, toCachedSpawners(r.spawnerInfos())));
                if (this.cache.debug()) {
                    System.out.printf("[DEBUG] cache miss seed=%d chunk=(%d,%d) spawners=%d%n",
                            worldSeed, chunkX, chunkZ, result.map(r -> r.spawnerInfos().size()).orElse(0));
                }
                return result;
            }
        }
        return doGenerate(worldSeed, chunkX, chunkZ);
    }

    private java.util.Optional<ChamberResult> doGenerate(long worldSeed, int chunkX, int chunkZ) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(worldSeed, chunkX, chunkZ);

        // start_height: uniform [-40, -20] — first B-flow RNG consumption.
        int startY = -40 + random.nextInt(TrialChambersData.START_HEIGHT_MAX - TrialChambersData.START_HEIGHT_MIN + 1);
        BlockPos startPos = new BlockPos(chunkX * 16, startY, chunkZ * 16);

        PoolAliasLookup aliasLookup = PoolAliasLookup.create(TrialChambersData.ALIAS_BINDINGS, startPos, worldSeed);

        ResourceKey<StructureTemplatePool> startKey = ResourceKey.create(TrialChambersData.START_POOL);
        GenerationContext context = new GenerationContext(
                this.templateManager, this.pools, random, worldSeed, this.heightAccessor);

        int maxDepth = this.jigsawDepth > 0 ? this.jigsawDepth : TrialChambersData.SIZE;
        return JigsawPlacement.addPieces(
                context,
                Holder.reference(startKey),
                maxDepth,
                startPos,
                new MaxDistance(TrialChambersData.MAX_DISTANCE_FROM_CENTER),
                aliasLookup,
                new DimensionPadding(TrialChambersData.DIMENSION_PADDING, TrialChambersData.DIMENSION_PADDING),
                LiquidSettings.IGNORE_WATERLOGGING)
                .map(result -> {
                    List<SpawnerInfo> infos = collectSpawnerInfos(result);
                    List<BlockPos> positions = infos.stream().map(SpawnerInfo::pos).toList();
                    return new ChamberResult(result, positions, resolveMobAliases(aliasLookup), infos);
                });
    }

    /**
     * Collects every trial-spawner block across all assembled pieces, together with its resolved
     * mob type. The mob is read from the spawner block's NBT {@code normal_config} field, e.g.
     * {@code minecraft:trial_chamber/ranged/skeleton/normal} → {@code "skeleton"}.
     */
    private List<SpawnerInfo> collectSpawnerInfos(JigsawPlacement.JigsawResult result) {
        List<SpawnerInfo> spawners = new ArrayList<>();
        for (PoolElementStructurePiece piece : result.pieces()) {
            StructurePoolElement element = piece.getElement();
            if (element instanceof SinglePoolElement single) {
                StructureTemplate template = single.getTemplate(this.templateManager);
                List<StructureBlockInfo> infos = template.filterBlocks(
                        piece.getPosition(),
                        new StructurePlaceSettings().setRotation(piece.getRotation()),
                        "minecraft:trial_spawner");
                for (StructureBlockInfo info : infos) {
                    spawners.add(new SpawnerInfo(info.pos(), extractMob(info.nbt())));
                }
            }
        }
        spawners.sort(Comparator.comparing(SpawnerInfo::pos));
        return spawners;
    }

    private static String extractMob(NbtTag.Compound nbt) {
        if (nbt == null || !nbt.contains("normal_config")) {
            return "unknown";
        }
        String config = nbt.getString("normal_config");
        String path = config.endsWith("/normal")
                ? config.substring(0, config.length() - "/normal".length())
                : config.endsWith("/ominous")
                        ? config.substring(0, config.length() - "/ominous".length())
                        : config;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static List<SpawnerCache.SpawnerData> toCachedSpawners(List<SpawnerInfo> infos) {
        List<SpawnerCache.SpawnerData> list = new ArrayList<>(infos.size());
        for (SpawnerInfo info : infos) {
            list.add(new SpawnerCache.SpawnerData(
                    info.pos().getX(), info.pos().getY(), info.pos().getZ(), info.mob()));
        }
        return list;
    }

    /** Resolves the C-flow mob-type aliases for this chamber and exposes them for reporting. */
    private MobAliases resolveMobAliases(PoolAliasLookup lookup) {
        return new MobAliases(
                lookup.lookup(TrialChambersData.spawnerKey("contents/ranged")).identifier().getPath(),
                lookup.lookup(TrialChambersData.spawnerKey("contents/slow_ranged")).identifier().getPath(),
                lookup.lookup(TrialChambersData.spawnerKey("contents/melee")).identifier().getPath(),
                lookup.lookup(TrialChambersData.spawnerKey("contents/small_melee")).identifier().getPath());
    }

    /** A trial-spawner block within a chamber, with its resolved mob type. */
    public record SpawnerInfo(BlockPos pos, String mob) {
    }

    public record ChamberResult(
            JigsawPlacement.JigsawResult assembly,
            List<BlockPos> spawnerPositions,
            MobAliases mobAliases,
            List<SpawnerInfo> spawnerInfos) {

        /**
         * Builds a result from a cache entry. The assembly and mob-alias data are not cached
         * (only the spawner positions and mob types), so both are {@code null} here.
         */
        public static ChamberResult fromCached(List<SpawnerCache.SpawnerData> cached) {
            List<SpawnerInfo> infos = cached.stream()
                    .map(s -> new SpawnerInfo(new BlockPos(s.x(), s.y(), s.z()), s.mob()))
                    .toList();
            List<BlockPos> positions = infos.stream().map(SpawnerInfo::pos).toList();
            return new ChamberResult(null, positions, null, infos);
        }
    }

    public record MobAliases(String ranged, String slowRanged, String melee, String smallMelee) {
        @Override
        public String toString() {
            return "MobAliases[ranged=" + ranged + ", slowRanged=" + slowRanged + ", melee=" + melee + ", smallMelee=" + smallMelee + "]";
        }
    }
}
