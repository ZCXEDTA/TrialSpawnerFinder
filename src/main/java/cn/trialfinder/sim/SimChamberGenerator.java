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
import cn.trialfinder.sim.structure.pools.LightJigsawPlacement;
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
    /** Template id -> trial-spawner block count, for the checkpoint predictor (built at preload). */
    private java.util.Map<cn.trialfinder.sim.resources.Identifier, Integer> spawnerCountByTemplate;

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
        java.util.Map<cn.trialfinder.sim.resources.Identifier, Integer> counts = new java.util.HashMap<>();
        for (cn.trialfinder.sim.structure.pools.StructureTemplatePool pool
                : this.pools.pools().values()) {
            for (cn.trialfinder.sim.util.Pair<StructurePoolElement, Integer> entry : pool.getTemplates()) {
                entry.first().collectTemplateIds(id -> counts.put(id, preloadTemplate(id)));
            }
        }
        this.spawnerCountByTemplate = java.util.Map.copyOf(counts);
        System.out.printf("[timing] template preload (NBT)           %.1f ms%n",
                (System.nanoTime() - t0) / 1e6);
    }

    private int preloadTemplate(cn.trialfinder.sim.resources.Identifier id) {
        // Load the NBT template into the manager cache (single-threaded I/O), but do NOT precompute
        // per-rotation jigsaw/bbox transforms here: pre-filling those caches empirically slowed
        // parallel B-flow (the cached lists get shallow-copied per call regardless, and the added
        // pre-fill allocation/garbage outweighed the computeIfAbsent-miss savings).
        // Also record how many trial_spawner blocks the template has (used by the checkpoint
        // predictor; most templates have none).
        cn.trialfinder.sim.structure.StructureTemplate template =
                this.templateManager.get(id).orElse(null);
        return template != null ? template.countBlocks("minecraft:trial_spawner") : 0;
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
                SpawnerCache.CachedChamber cached = this.cache.get(worldSeed, chunkX, chunkZ);
                if (cached != null) {
                    if (this.cache.debug()) {
                        System.out.printf("[DEBUG] cache hit  seed=%d chunk=(%d,%d) spawners=%d vaults=%d%n",
                                worldSeed, chunkX, chunkZ, cached.spawners().size(), cached.vaults().size());
                    }
                    return java.util.Optional.of(ChamberResult.fromCached(cached.spawners(), cached.vaults()));
                }
                java.util.Optional<ChamberResult> result = doGenerate(worldSeed, chunkX, chunkZ);
                result.ifPresent(r -> this.cache.put(worldSeed, chunkX, chunkZ,
                        toCachedSpawners(r.spawnerInfos()), toCachedVaults(r.vaultInfos())));
                if (this.cache.debug()) {
                    System.out.printf("[DEBUG] cache miss seed=%d chunk=(%d,%d) spawners=%d vaults=%d%n",
                            worldSeed, chunkX, chunkZ,
                            result.map(r -> r.spawnerInfos().size()).orElse(0),
                            result.map(r -> r.vaultInfos().size()).orElse(0));
                }
                return result;
            }
        }
        return doGenerate(worldSeed, chunkX, chunkZ);
    }

    /**
     * Counts the trial-spawner blocks across the placed pieces using the per-template map (no RNG
     * consumption, no per-piece block scan). This is a lower bound on the full chamber's spawner
     * count — the checkpoint predictor uses it to decide whether to keep or drop a candidate.
     */
    public int countSpawners(java.util.List<cn.trialfinder.sim.structure.pools.PoolElementStructurePiece> pieces) {
        int count = 0;
        for (cn.trialfinder.sim.structure.pools.PoolElementStructurePiece piece : pieces) {
            cn.trialfinder.sim.structure.pools.StructurePoolElement element = piece.getElement();
            if (element instanceof cn.trialfinder.sim.structure.pools.SinglePoolElement single) {
                Integer perTemplate = this.spawnerCountByTemplate.get(single.getTemplateLocation());
                if (perTemplate != null) {
                    count += perTemplate;
                }
            }
        }
        return count;
    }

    /**
     * Generates a chamber with an optional checkpoint prefilter. When {@code checkpointDepth > 0}
     * and {@code verifyGate > 0}, assembly pauses at the first piece of {@code checkpointDepth}; if
     * the placed pieces' spawner count is below {@code verifyGate}, the chamber is dropped
     * ({@link java.util.Optional#empty()} — cheaper than a full assembly). Otherwise assembly
     * resumes and the result is identical to {@link #generate}. {@code checkpointDepth <= 0} (or
     * {@code >= maxDepth}) behaves exactly like {@link #generate}.
     */
    public java.util.Optional<ChamberResult> generateWithCheckpoint(
            long worldSeed, int chunkX, int chunkZ, int checkpointDepth, int verifyGate) {
        if (this.cache != null && this.cache.isEnabled()) {
            synchronized (this.cache.lockFor(worldSeed, chunkX, chunkZ)) {
                SpawnerCache.CachedChamber cached = this.cache.get(worldSeed, chunkX, chunkZ);
                if (cached != null) {
                    return java.util.Optional.of(ChamberResult.fromCached(cached.spawners(), cached.vaults()));
                }
                java.util.Optional<ChamberResult> result = doGenerateWithCheckpoint(
                        worldSeed, chunkX, chunkZ, checkpointDepth, verifyGate);
                // Only persist chambers that survived the checkpoint (dropped ones are cheap to re-predict).
                result.ifPresent(r -> this.cache.put(worldSeed, chunkX, chunkZ,
                        toCachedSpawners(r.spawnerInfos()), toCachedVaults(r.vaultInfos())));
                return result;
            }
        }
        return doGenerateWithCheckpoint(worldSeed, chunkX, chunkZ, checkpointDepth, verifyGate);
    }

    private java.util.Optional<ChamberResult> doGenerateWithCheckpoint(
            long worldSeed, int chunkX, int chunkZ, int checkpointDepth, int verifyGate) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(worldSeed, chunkX, chunkZ);

        int startY = -40 + random.nextInt(TrialChambersData.START_HEIGHT_MAX - TrialChambersData.START_HEIGHT_MIN + 1);
        BlockPos startPos = new BlockPos(chunkX * 16, startY, chunkZ * 16);

        PoolAliasLookup aliasLookup = PoolAliasLookup.create(TrialChambersData.ALIAS_BINDINGS, startPos, worldSeed);

        ResourceKey<StructureTemplatePool> startKey = ResourceKey.create(TrialChambersData.START_POOL);
        GenerationContext context = new GenerationContext(
                this.templateManager, this.pools, random, worldSeed, this.heightAccessor);

        int maxDepth = this.jigsawDepth > 0 ? this.jigsawDepth : TrialChambersData.SIZE;
        // At/above max depth the checkpoint would never fire before the tree ends; degenerate to full.
        boolean checkpointActive = checkpointDepth > 0 && checkpointDepth < maxDepth && verifyGate > 0;
        JigsawPlacement.CheckpointedResult cr = JigsawPlacement.addPiecesWithCheckpoint(
                context,
                Holder.reference(startKey),
                maxDepth,
                startPos,
                new MaxDistance(TrialChambersData.MAX_DISTANCE_FROM_CENTER),
                aliasLookup,
                new DimensionPadding(TrialChambersData.DIMENSION_PADDING, TrialChambersData.DIMENSION_PADDING),
                LiquidSettings.IGNORE_WATERLOGGING,
                checkpointActive ? checkpointDepth : 0,
                checkpointActive ? (placed, depth) -> countSpawners(placed) >= verifyGate : null);
        if (cr.stoppedEarly()) {
            return java.util.Optional.empty();
        }
        return cr.result().map(result -> {
            List<SpawnerInfo> infos = collectSpawnerInfos(result);
            List<BlockPos> positions = infos.stream().map(SpawnerInfo::pos).toList();
            List<VaultInfo> vaults = collectVaultInfos(result);
            return new ChamberResult(result, positions, resolveMobAliases(aliasLookup), infos, vaults);
        });
    }

    /**
     * Measures the shallow (depth &lt; {@code checkpointDepth}) spawner count without completing the
     * assembly: runs the checkpoint with a recording callback that captures the spawner count of the
     * placed pieces then stops. This is the predictor value used by the checkpoint gate.
     */
    public int shallowSpawnerCount(long worldSeed, int chunkX, int chunkZ, int checkpointDepth) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(worldSeed, chunkX, chunkZ);

        int startY = -40 + random.nextInt(TrialChambersData.START_HEIGHT_MAX - TrialChambersData.START_HEIGHT_MIN + 1);
        BlockPos startPos = new BlockPos(chunkX * 16, startY, chunkZ * 16);

        PoolAliasLookup aliasLookup = PoolAliasLookup.create(TrialChambersData.ALIAS_BINDINGS, startPos, worldSeed);
        ResourceKey<StructureTemplatePool> startKey = ResourceKey.create(TrialChambersData.START_POOL);
        GenerationContext context = new GenerationContext(
                this.templateManager, this.pools, random, worldSeed, this.heightAccessor);
        int maxDepth = this.jigsawDepth > 0 ? this.jigsawDepth : TrialChambersData.SIZE;
        int[] recorded = {-1}; // sentinel: checkpoint never fired (tree ended shallower than depth)
        JigsawPlacement.CheckpointedResult cr = JigsawPlacement.addPiecesWithCheckpoint(
                context,
                Holder.reference(startKey),
                maxDepth,
                startPos,
                new MaxDistance(TrialChambersData.MAX_DISTANCE_FROM_CENTER),
                aliasLookup,
                new DimensionPadding(TrialChambersData.DIMENSION_PADDING, TrialChambersData.DIMENSION_PADDING),
                LiquidSettings.IGNORE_WATERLOGGING,
                checkpointDepth,
                (placed, depth) -> {
                    recorded[0] = countSpawners(placed);
                    return false; // stop immediately after recording
                });
        if (recorded[0] < 0) {
            // The tree never reached checkpointDepth: the shallow result IS the full result.
            return cr.result()
                    .map(r -> countSpawners(r.pieces()))
                    .orElse(0);
        }
        return recorded[0];
    }

    /**
     * Predicts the chamber's spawner count via a full assembly but WITHOUT the per-piece block scan
     * ({@code collectSpawnerInfos}). Runs the same Jigsaw layout as {@link #doGenerate}, then sums
     * the per-template spawner map ({@link #countSpawners}). This is an exact upper bound on the
     * full chamber's spawner count and is significantly cheaper than full generation (no
     * {@code filterBlocks} scan), making it suitable for cluster-level prefiltering.
     */
    public int predictSpawnerCount(long worldSeed, int chunkX, int chunkZ) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(worldSeed, chunkX, chunkZ);

        int startY = -40 + random.nextInt(TrialChambersData.START_HEIGHT_MAX - TrialChambersData.START_HEIGHT_MIN + 1);
        BlockPos startPos = new BlockPos(chunkX * 16, startY, chunkZ * 16);

        PoolAliasLookup aliasLookup = PoolAliasLookup.create(TrialChambersData.ALIAS_BINDINGS, startPos, worldSeed);
        ResourceKey<StructureTemplatePool> startKey = ResourceKey.create(TrialChambersData.START_POOL);
        GenerationContext context = new GenerationContext(
                this.templateManager, this.pools, random, worldSeed, this.heightAccessor);
        int maxDepth = this.jigsawDepth > 0 ? this.jigsawDepth : TrialChambersData.SIZE;
        // Lightweight assembly: identical RNG consumption to JigsawPlacement.addPieces, but avoids
        // the per-chamber object allocations (LightPiece + ConnectorBuffer + ElementMetadata cache).
        return LightJigsawPlacement.predictSpawners(
                context,
                Holder.reference(startKey),
                maxDepth,
                startPos,
                new MaxDistance(TrialChambersData.MAX_DISTANCE_FROM_CENTER),
                aliasLookup,
                new DimensionPadding(TrialChambersData.DIMENSION_PADDING, TrialChambersData.DIMENSION_PADDING),
                LiquidSettings.IGNORE_WATERLOGGING)
                .map(List::size)
                .orElse(0);
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
                    List<VaultInfo> vaults = collectVaultInfos(result);
                    return new ChamberResult(result, positions, resolveMobAliases(aliasLookup), infos, vaults);
                });
    }

    /**
     * Collects every trial-spawner block across all assembled pieces, together with its resolved
     * mob type and trial-spawner config id. The mob/config are read from the spawner block's NBT
     * {@code normal_config} field, e.g. {@code minecraft:trial_chamber/ranged/skeleton/normal}
     * → mob {@code "skeleton"}, config {@code "minecraft:trial_chamber/ranged/skeleton/normal"}.
     */
    private List<SpawnerInfo> collectSpawnerInfos(JigsawPlacement.JigsawResult result) {
        List<SpawnerInfo> spawners = new ArrayList<>();
        for (PoolElementStructurePiece piece : result.pieces()) {
            StructurePoolElement element = piece.getElement();
            if (element instanceof SinglePoolElement single) {
                StructureTemplate template = single.getTemplate(this.templateManager);
                // Use the per-rotation spawner-block cache: avoids re-running the rotation/state
                // transform for every assembled piece (templates rarely have spawners, so the
                // cached list is 0-2 entries and the offset is a cheap per-entry BlockPos offset).
                BlockPos piecePos = piece.getPosition();
                for (StructureBlockInfo info : template.getSpawnerBlocks(piece.getRotation())) {
                    BlockPos world = info.pos().offset(piecePos);
                    String config = extractConfig(info.nbt());
                    spawners.add(new SpawnerInfo(world, extractMobFromConfig(config), config));
                }
            }
        }
        spawners.sort(Comparator.comparing(SpawnerInfo::pos));
        return spawners;
    }

    /**
     * Collects every vault block across all assembled pieces, together with whether the vault is the
     * ominous variant. Vaults come from the {@code reward/vault} (normal) and
     * {@code reward/ominous_vault} (ominous) templates; the template id is used to distinguish them.
     */
    private List<VaultInfo> collectVaultInfos(JigsawPlacement.JigsawResult result) {
        List<VaultInfo> vaults = new ArrayList<>();
        for (PoolElementStructurePiece piece : result.pieces()) {
            StructurePoolElement element = piece.getElement();
            if (element instanceof SinglePoolElement single) {
                StructureTemplate template = single.getTemplate(this.templateManager);
                List<StructureBlockInfo> infos = template.filterBlocks(
                        piece.getPosition(),
                        new StructurePlaceSettings().setRotation(piece.getRotation()),
                        "minecraft:vault");
                boolean ominous = isOminousVaultTemplate(single);
                for (StructureBlockInfo info : infos) {
                    vaults.add(new VaultInfo(info.pos(), ominous));
                }
            }
        }
        vaults.sort(Comparator.comparing(VaultInfo::pos));
        return vaults;
    }

    /** True when the piece's template is the ominous-vault reward template. */
    private static boolean isOminousVaultTemplate(SinglePoolElement single) {
        String path = single.getTemplateLocation().getPath();
        return path.contains("ominous_vault");
    }

    /** Returns the {@code normal_config} id of a spawner block's NBT, or {@code null}. */
    private static String extractConfig(NbtTag.Compound nbt) {
        if (nbt == null || !nbt.contains("normal_config")) {
            return null;
        }
        return nbt.getString("normal_config");
    }

    /** Derives the mob name from a config id, e.g. {@code ".../ranged/skeleton/normal"} → {@code "skeleton"}. */
    static String extractMobFromConfig(String config) {
        if (config == null || config.isEmpty()) {
            return "unknown";
        }
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
                    info.pos().getX(), info.pos().getY(), info.pos().getZ(), info.mob(), info.config()));
        }
        return list;
    }

    private static List<SpawnerCache.VaultData> toCachedVaults(List<VaultInfo> vaults) {
        List<SpawnerCache.VaultData> list = new ArrayList<>(vaults.size());
        for (VaultInfo vault : vaults) {
            list.add(new SpawnerCache.VaultData(
                    vault.pos().getX(), vault.pos().getY(), vault.pos().getZ(), vault.ominous()));
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

    /**
     * A trial-spawner block within a chamber, with its resolved mob type and trial-spawner config
     * id (e.g. {@code "minecraft:trial_chamber/ranged/skeleton/normal"}); {@code config} may be null.
     */
    public record SpawnerInfo(BlockPos pos, String mob, String config) {
        public SpawnerInfo(BlockPos pos, String mob) {
            this(pos, mob, null);
        }
    }

    public record ChamberResult(
            JigsawPlacement.JigsawResult assembly,
            List<BlockPos> spawnerPositions,
            MobAliases mobAliases,
            List<SpawnerInfo> spawnerInfos,
            List<VaultInfo> vaultInfos) {

        /**
         * Builds a result from a cache entry. The assembly and mob-alias data are not cached
         * (only the spawner positions/mob types and vault positions), so both are {@code null} here.
         */
        public static ChamberResult fromCached(List<SpawnerCache.SpawnerData> cached,
                                               List<SpawnerCache.VaultData> cachedVaults) {
            List<SpawnerInfo> infos = cached.stream()
                    .map(s -> new SpawnerInfo(new BlockPos(s.x(), s.y(), s.z()), s.mob(), s.config()))
                    .toList();
            List<BlockPos> positions = infos.stream().map(SpawnerInfo::pos).toList();
            List<VaultInfo> vaults = cachedVaults.stream()
                    .map(v -> new VaultInfo(new BlockPos(v.x(), v.y(), v.z()), v.ominous()))
                    .toList();
            return new ChamberResult(null, positions, null, infos, vaults);
        }
    }

    /** A vault block within a chamber: its position and whether it is the ominous variant. */
    public record VaultInfo(BlockPos pos, boolean ominous) {
    }

    public record MobAliases(String ranged, String slowRanged, String melee, String smallMelee) {
        @Override
        public String toString() {
            return "MobAliases[ranged=" + ranged + ", slowRanged=" + slowRanged + ", melee=" + melee + ", smallMelee=" + smallMelee + "]";
        }
    }
}
