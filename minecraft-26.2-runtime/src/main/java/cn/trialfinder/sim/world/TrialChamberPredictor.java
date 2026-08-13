package cn.trialfinder.sim.world;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;
import cn.trialfinder.sim.data.PoolAliasLookup;
import cn.trialfinder.sim.jigsaw.FreeSpace;
import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.BoundingBox;
import cn.trialfinder.sim.math.ChunkPos;
import cn.trialfinder.sim.math.Direction;
import cn.trialfinder.sim.math.Rotation;
import cn.trialfinder.sim.pool.DimensionPadding;
import cn.trialfinder.sim.pool.EmptyPoolElement;
import cn.trialfinder.sim.pool.ListPoolElement;
import cn.trialfinder.sim.pool.MaxDistance;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.pool.SinglePoolElement;
import cn.trialfinder.sim.pool.StructurePoolElement;
import cn.trialfinder.sim.pool.StructureTemplatePool;
import cn.trialfinder.sim.random.LegacyRandomSource;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.random.SingleThreadedRandomSource;
import cn.trialfinder.sim.random.WorldgenRandom;
import cn.trialfinder.sim.resource.Identifier;
import cn.trialfinder.sim.resource.ResourceKey;
import cn.trialfinder.sim.template.JigsawBlock;
import cn.trialfinder.sim.template.JigsawBlockInfo;
import cn.trialfinder.sim.template.JointType;
import cn.trialfinder.sim.template.StructureBlockInfo;
import cn.trialfinder.sim.template.StructurePlaceSettings;
import cn.trialfinder.sim.template.StructureTemplate;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.util.SequencedPriorityIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 26.2 试炼密室布局的轻量计算器——由 {@code HandTrialChamberPredictor} 剥离改造而来。
 * 随机调用与放置顺序镜像 {@code JigsawPlacement}；不可变模板元数据缓存。
 * 与官方实现共享同一套 Jigsaw 拼接算法，因此刷怪笼坐标逐位一致。
 *
 * <p>与 {@code HandTrialChamberPredictor} 的区别：所有 Minecraft 类型换成自有的纯 Java 类型，
 * 构造函数不再需要 {@code ServerLevel}；生物群系判定（Phase 1）省略。
 */
public final class TrialChamberPredictor {
    private static final Rotation[] ROTATIONS = Rotation.values();
    private static final ConcurrentMap<StructurePoolElement, ElementMetadata[]> METADATA_CACHE =
            new ConcurrentHashMap<>();

    private final long seed;
    private final SimStructureConfig config;
    private final PoolRegistry pools;
    private final StructureTemplateManager templateManager;
    private final WorldgenRandom layoutRandom =
            new WorldgenRandom(new NonAtomicLegacyRandomSource(0L));
    private final ConnectorBuffer sourceConnectorBuffer = new ConnectorBuffer();
    private final ConnectorBuffer targetConnectorBuffer = new ConnectorBuffer();
    private final IdentityHashMap<StructurePoolElement, ElementMetadata[]> localMetadata =
            new IdentityHashMap<>();
    private final int maxDepth;
    private final boolean useExpansionHack;

    public TrialChamberPredictor(
            long seed, SimStructureConfig config, PoolRegistry pools,
            StructureTemplateManager templateManager) {
        this.seed = seed;
        this.config = config;
        this.pools = pools;
        this.templateManager = templateManager;
        this.maxDepth = config.size();
        this.useExpansionHack = config.useExpansionHack();
    }

    public Prediction predict(BlockPoint candidate) {
        ChunkPos chunkPos = new ChunkPos(
                Math.floorDiv(candidate.x(), 16), Math.floorDiv(candidate.z(), 16));
        WorldgenRandom random = layoutRandom;
        random.setLargeFeatureSeed(seed, chunkPos.x(), chunkPos.z());
        int startY = config.sampleStartHeight(random);
        BlockPos start = new BlockPos(
                chunkPos.getMinBlockX(), startY, chunkPos.getMinBlockZ());
        PoolAliasLookup aliases = PoolAliasLookup.create(
                config.poolAliases(), start, seed);
        StartLayout layout = createStart(start, random, aliases);
        if (layout == null) {
            return new Prediction(candidate, false, List.of());
        }

        List<LightPiece> pieces = new ArrayList<>();
        pieces.add(layout.piece());
        if (maxDepth > 0) {
            generateChildren(layout, pieces, random, aliases);
        }

        Set<SpawnerPoint> spawners = new HashSet<>();
        Set<SpawnerInfo> spawnerInfos = new HashSet<>();
        Set<VaultInfo> vaults = new HashSet<>();
        for (LightPiece piece : pieces) {
            collectSpawners(piece, spawners, spawnerInfos);
            collectVaults(piece, vaults);
        }
        List<SpawnerPoint> sorted = new ArrayList<>(spawners);
        sorted.sort(SpawnerPoint::compareTo);
        List<SpawnerInfo> infos = new ArrayList<>(spawnerInfos);
        infos.sort(Comparator.comparingInt(SpawnerInfo::x)
                .thenComparingInt(SpawnerInfo::y)
                .thenComparingInt(SpawnerInfo::z));
        List<VaultInfo> vaultList = new ArrayList<>(vaults);
        vaultList.sort(Comparator.comparingInt(VaultInfo::x)
                .thenComparingInt(VaultInfo::y)
                .thenComparingInt(VaultInfo::z));
        return new Prediction(candidate, !sorted.isEmpty(), sorted, infos, vaultList);
    }

    private StartLayout createStart(
            BlockPos start, WorldgenRandom random, PoolAliasLookup aliases) {
        Rotation rotation = Rotation.getRandom(random);
        ResourceKey<StructureTemplatePool> resolvedKey = aliases.lookup(config.startPool());
        StructureTemplatePool startPool = pools.getOrThrow(resolvedKey);
        StructurePoolElement element = startPool.getRandomTemplate(random);
        if (element == EmptyPoolElement.INSTANCE) {
            return null;
        }

        BlockPos anchoredPosition = start;
        if (config.startJigsawName().isPresent()) {
            Optional<BlockPos> found = findStartingJigsaw(
                    element, config.startJigsawName().get(), start, rotation, random);
            if (found.isEmpty()) {
                return null;
            }
            anchoredPosition = found.get();
        }

        BlockPos localAnchor = anchoredPosition.subtract(start);
        BlockPos piecePosition = start.subtract(localAnchor);
        BoundingBox box = boundingBox(element, piecePosition, rotation);
        int centerX = (box.maxX() + box.minX()) / 2;
        int centerZ = (box.maxZ() + box.minZ()) / 2;
        int groundY = config.projectStartToHeightmap()
                ? throwUnsupportedTerrainHeight("projectStartToHeightmap")
                : piecePosition.getY();
        int verticalOffset = groundY - (box.minY() + element.getGroundLevelDelta());
        piecePosition = piecePosition.offset(0, verticalOffset, 0);
        box = box.moved(0, verticalOffset, 0);

        DimensionPadding padding = config.dimensionPadding();
        if (padding != DimensionPadding.ZERO
                && (box.minY() < config.minY() + padding.bottom()
                || box.maxY() > config.maxY() - padding.top())) {
            return null;
        }

        LightPiece piece = new LightPiece(
                element, piecePosition, element.getGroundLevelDelta(), rotation, box);
        return new StartLayout(
                piece, new BlockPos(centerX, groundY + localAnchor.getY(), centerZ));
    }

    private Optional<BlockPos> findStartingJigsaw(
            StructurePoolElement element, Identifier name, BlockPos start,
            Rotation rotation, RandomSource random) {
        ConnectorBuffer connectors = fillConnectors(
                element, start, rotation, random, sourceConnectorBuffer);
        for (int index = 0; index < connectors.size(); index++) {
            Connector connector = connectors.get(index);
            if (name.equals(connector.name())) {
                return Optional.of(start.offset(connector.relativePos()));
            }
        }
        return Optional.empty();
    }

    private void generateChildren(
            StartLayout start, List<LightPiece> pieces, RandomSource random,
            PoolAliasLookup aliases) {
        LightPiece first = start.piece();
        BlockPos center = start.biomePosition();
        MaxDistance distance = config.maxDistance();
        DimensionPadding padding = config.dimensionPadding();
        int minY = config.minY();
        int maxY = config.maxY();
        int allowedMinX = center.getX() - distance.horizontal();
        int allowedMinY = Math.max(center.getY() - distance.vertical(), minY + padding.bottom());
        int allowedMinZ = center.getZ() - distance.horizontal();
        int allowedMaxXExclusive = center.getX() + distance.horizontal() + 1;
        int allowedMaxYExclusive = Math.min(center.getY() + distance.vertical() + 1,
                maxY + 1 - padding.top());
        int allowedMaxZExclusive = center.getZ() + distance.horizontal() + 1;
        FreeSpace shape = new FreeSpace(
                allowedMinX, allowedMinY, allowedMinZ,
                allowedMaxXExclusive, allowedMaxYExclusive, allowedMaxZExclusive,
                first.box());
        SequencedPriorityIterator<QueuedPiece> queue = new SequencedPriorityIterator<>();
        generatePiece(first, shape, 0, pieces, queue, random, aliases);
        while (queue.hasNext()) {
            QueuedPiece next = queue.next();
            generatePiece(
                    next.piece(), next.shape(), next.depth(), pieces, queue, random, aliases);
        }
    }

    private void generatePiece(
            LightPiece source, FreeSpace contextFree, int depth,
            List<LightPiece> pieces, SequencedPriorityIterator<QueuedPiece> queue,
            RandomSource random, PoolAliasLookup aliases) {
        boolean sourceRigid = source.element().getProjection()
                == StructurePoolElement.getRigidProjection();
        FreeSpace sourceFree = null;
        int sourceBoxY = source.box().minY();

        ConnectorBuffer sourceConnectors = fillConnectors(
                source.element(), source.position(), source.rotation(), random,
                sourceConnectorBuffer);
        sourceConnectorLoop:
        for (int sourceIndex = 0; sourceIndex < sourceConnectors.size(); sourceIndex++) {
            Connector sourceConnector = sourceConnectors.get(sourceIndex);
            Direction sourceDirection = sourceConnector.front();
            BlockPos sourceJigsawPos = source.position().offset(sourceConnector.relativePos());
            BlockPos targetJigsawPos = sourceJigsawPos.relative(sourceDirection);
            int sourceJigsawLocalY = sourceJigsawPos.getY() - sourceBoxY;
            int sourceJigsawBaseHeight = Integer.MIN_VALUE;

            ResourceKey<StructureTemplatePool> poolKey = aliases.lookup(sourceConnector.pool());
            Optional<StructureTemplatePool> selectedEntry = pools.get(poolKey);
            if (selectedEntry.isEmpty()) {
                continue;
            }
            StructureTemplatePool selectedPool = selectedEntry.get();
            StructureTemplatePool fallbackPool = selectedPool.getFallback().value();

            boolean attachInsideSource = source.box().isInside(targetJigsawPos);
            FreeSpace childrenFree;
            if (attachInsideSource) {
                if (sourceFree == null) {
                    BoundingBox box = source.box();
                    sourceFree = new FreeSpace(
                            box.minX(), box.minY(), box.minZ(),
                            box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1, null);
                }
                childrenFree = sourceFree;
            } else {
                childrenFree = contextFree;
            }

            List<StructurePoolElement> targetElements = new ArrayList<>();
            if (depth != maxDepth) {
                targetElements.addAll(selectedPool.getShuffledTemplates(random));
            }
            targetElements.addAll(fallbackPool.getShuffledTemplates(random));
            int placementPriority = sourceConnector.placementPriority();

            for (StructurePoolElement targetElement : targetElements) {
                if (targetElement == EmptyPoolElement.INSTANCE) {
                    break;
                }
                for (Rotation targetRotation : Rotation.getShuffled(random)) {
                    ConnectorBuffer targetConnectors = fillConnectors(
                            targetElement, BlockPos.ZERO, targetRotation, random,
                            targetConnectorBuffer);
                    BoundingBox hackBox = boundingBox(
                            targetElement, BlockPos.ZERO, targetRotation);
                    int expandTo = useExpansionHack && hackBox.getYSpan() <= 16
                            ? expansionSize(targetConnectors, hackBox, aliases)
                            : 0;

                    for (int targetIndex = 0;
                            targetIndex < targetConnectors.size(); targetIndex++) {
                        Connector targetConnector = targetConnectors.get(targetIndex);
                        if (!canAttach(sourceConnector, targetConnector)) {
                            continue;
                        }
                        BlockPos targetJigsawLocalPos = targetConnector.relativePos();
                        BlockPos rawTargetPosition =
                                targetJigsawPos.subtract(targetJigsawLocalPos);
                        BoundingBox rawTargetBox = boundingBox(
                                targetElement, rawTargetPosition, targetRotation);
                        int rawTargetY = rawTargetBox.minY();
                        boolean targetRigid = targetElement.getProjection()
                                == StructurePoolElement.getRigidProjection();
                        int targetJigsawLocalY = targetJigsawLocalPos.getY();
                        int deltaY = sourceJigsawLocalY - targetJigsawLocalY
                                + sourceDirection.getStepY();
                        int targetBoxY;
                        if (sourceRigid && targetRigid) {
                            targetBoxY = sourceBoxY + deltaY;
                        } else {
                            throwUnsupportedTerrainHeight("非 RIGID piece 拼接");
                            return;
                        }

                        int verticalOffset = targetBoxY - rawTargetY;
                        BoundingBox targetBox = rawTargetBox.moved(0, verticalOffset, 0);
                        BlockPos targetPosition =
                                rawTargetPosition.offset(0, verticalOffset, 0);
                        if (expandTo > 0) {
                            int newSize = Math.max(
                                    expandTo + 1, targetBox.maxY() - targetBox.minY());
                            targetBox = targetBox.encapsulate(new BlockPos(
                                    targetBox.minX(), targetBox.minY() + newSize,
                                    targetBox.minZ()));
                        }
                        if (!childrenFree.contains(targetBox)) {
                            continue;
                        }

                        childrenFree.occupy(targetBox);
                        int targetGroundDelta = targetRigid
                                ? source.groundLevelDelta() - deltaY
                                : targetElement.getGroundLevelDelta();
                        LightPiece target = new LightPiece(
                                targetElement, targetPosition, targetGroundDelta,
                                targetRotation, targetBox);
                        pieces.add(target);
                        if (depth + 1 <= maxDepth) {
                            queue.add(
                                    new QueuedPiece(target, childrenFree, depth + 1),
                                    placementPriority);
                        }
                        continue sourceConnectorLoop;
                    }
                }
            }
        }
    }

    private int expansionSize(
            ConnectorBuffer connectors, BoundingBox box, PoolAliasLookup aliases) {
        int largest = 0;
        for (int index = 0; index < connectors.size(); index++) {
            Connector connector = connectors.get(index);
            if (!box.isInside(connector.relativePos().relative(connector.front()))) {
                continue;
            }
            ResourceKey<StructureTemplatePool> key = aliases.lookup(connector.pool());
            Optional<StructureTemplatePool> pool = pools.get(key);
            if (pool.isEmpty()) {
                continue;
            }
            StructureTemplatePool value = pool.get();
            largest = Math.max(largest, value.getMaxSize(templateManager));
            largest = Math.max(largest, value.getFallback().value().getMaxSize(templateManager));
        }
        return largest;
    }

    private static boolean canAttach(Connector source, Connector target) {
        return source.front() == target.front().getOpposite()
                && (source.rollable() || source.top() == target.top())
                && source.target().equals(target.name());
    }

    private ConnectorBuffer fillConnectors(
            StructurePoolElement element, BlockPos position,
            Rotation rotation, RandomSource random, ConnectorBuffer output) {
        ElementMetadata metadata = metadata(element, rotation);
        if (!metadata.supported()) {
            output.copyFrom(toConnectors(
                    element.getShuffledJigsawBlocks(
                            templateManager, position, rotation, random),
                    position));
            return output;
        }
        output.copyFrom(metadata.connectors());
        orderConnectors(output.values, output.size, random);
        return output;
    }

    static void orderConnectors(Connector[] connectors, RandomSource random) {
        orderConnectors(connectors, connectors.length, random);
    }

    private static void orderConnectors(
            Connector[] connectors, int size, RandomSource random) {
        for (int remaining = size; remaining > 1; remaining--) {
            int selected = random.nextInt(remaining);
            Connector last = connectors[remaining - 1];
            connectors[remaining - 1] = connectors[selected];
            connectors[selected] = last;
        }
        for (int index = 1; index < size; index++) {
            Connector current = connectors[index];
            int insertion = index;
            while (insertion > 0
                    && connectors[insertion - 1].selectionPriority()
                    < current.selectionPriority()) {
                connectors[insertion] = connectors[insertion - 1];
                insertion--;
            }
            connectors[insertion] = current;
        }
    }

    private BoundingBox boundingBox(
            StructurePoolElement element, BlockPos position, Rotation rotation) {
        ElementMetadata metadata = metadata(element, rotation);
        if (!metadata.supported()) {
            return element.getBoundingBox(templateManager, position, rotation);
        }
        return metadata.boundingBox().moved(
                position.getX(), position.getY(), position.getZ());
    }

    /** 收集宝库方块：普通宝库来自 reward/vault 模板，不祥宝库来自 reward/ominous_vault。 */
    private void collectVaults(LightPiece piece, Set<VaultInfo> output) {
        StructurePoolElement element = piece.element();
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : list.getElements()) {
                collectVaultsElement(child, piece, output);
            }
            return;
        }
        collectVaultsElement(element, piece, output);
    }

    private void collectVaultsElement(
            StructurePoolElement element, LightPiece piece, Set<VaultInfo> output) {
        if (!(element instanceof SinglePoolElement single)) {
            return;
        }
        StructureTemplate template =
                templateManager.getOrCreate(single.getTemplateLocation());
        StructurePlaceSettings placement =
                new StructurePlaceSettings().setRotation(piece.rotation());
        boolean ominous = single.getTemplateLocation().getPath().contains("ominous_vault");
        for (StructureBlockInfo block :
                template.filterBlocks(piece.position(), placement, "minecraft:vault")) {
            output.add(new VaultInfo(
                    block.pos().getX(), block.pos().getY(), block.pos().getZ(), ominous));
        }
    }

    private void collectSpawners(
            LightPiece piece, Set<SpawnerPoint> output, Set<SpawnerInfo> infos) {
        ElementMetadata metadata = metadata(piece.element(), piece.rotation());
        if (metadata.supported()) {
            for (RelativeSpawner relative : metadata.spawners()) {
                SpawnerPoint point = new SpawnerPoint(
                        piece.position().getX() + relative.pos().getX(),
                        piece.position().getY() + relative.pos().getY(),
                        piece.position().getZ() + relative.pos().getZ());
                output.add(point);
                infos.add(new SpawnerInfo(
                        point.x(), point.y(), point.z(),
                        relative.normalConfig(), relative.ominousConfig()));
            }
            return;
        }
        collectSpawnersSlow(
                piece.element(), piece.position(), piece.rotation(), output, infos);
    }

    private void collectSpawnersSlow(
            StructurePoolElement element, BlockPos position,
            Rotation rotation, Set<SpawnerPoint> output, Set<SpawnerInfo> infos) {
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : list.getElements()) {
                collectSpawnersSlow(child, position, rotation, output, infos);
            }
            return;
        }
        if (!(element instanceof SinglePoolElement single)) {
            return;
        }
        StructureTemplate template =
                templateManager.getOrCreate(single.getTemplateLocation());
        StructurePlaceSettings placement =
                new StructurePlaceSettings().setRotation(rotation);
        for (StructureBlockInfo block :
                template.filterBlocks(position, placement, "minecraft:trial_spawner")) {
            SpawnerPoint point = new SpawnerPoint(
                    block.pos().getX(), block.pos().getY(), block.pos().getZ());
            output.add(point);
            String normal = "";
            String ominous = "";
            if (block.nbt() != null) {
                normal = block.nbt().getString("normal_config");
                ominous = block.nbt().getString("ominous_config");
            }
            infos.add(new SpawnerInfo(point.x(), point.y(), point.z(), normal, ominous));
        }
    }

    private ElementMetadata metadata(
            StructurePoolElement element, Rotation rotation) {
        if (element == EmptyPoolElement.INSTANCE) {
            return ElementMetadata.UNSUPPORTED;
        }
        ElementMetadata[] variants = localMetadata.computeIfAbsent(
                element, key -> METADATA_CACHE.computeIfAbsent(
                        key, ignored -> new ElementMetadata[ROTATIONS.length]));
        int index = rotation.ordinal();
        ElementMetadata cached = variants[index];
        if (cached != null) {
            return cached;
        }
        synchronized (variants) {
            cached = variants[index];
            if (cached == null) {
                cached = createMetadata(element, rotation);
                variants[index] = cached;
            }
        }
        return cached;
    }

    private ElementMetadata createMetadata(
            StructurePoolElement element, Rotation rotation) {
        if (element instanceof ListPoolElement list) {
            List<StructurePoolElement> children = list.getElements();
            if (children.isEmpty()) {
                return ElementMetadata.UNSUPPORTED;
            }
            ElementMetadata first = metadata(children.getFirst(), rotation);
            if (!first.supported()) {
                return ElementMetadata.UNSUPPORTED;
            }
            List<RelativeSpawner> spawners = new ArrayList<>();
            for (StructurePoolElement child : children) {
                ElementMetadata childMetadata = metadata(child, rotation);
                if (!childMetadata.supported()) {
                    return ElementMetadata.UNSUPPORTED;
                }
                spawners.addAll(childMetadata.spawners());
            }
            return new ElementMetadata(
                    first.connectors(),
                    element.getBoundingBox(templateManager, BlockPos.ZERO, rotation),
                    List.copyOf(spawners), true);
        }
        if (element instanceof SinglePoolElement single) {
            StructureTemplate template =
                    templateManager.getOrCreate(single.getTemplateLocation());
            List<RelativeSpawner> spawners = template.getSpawnerBlocks(rotation)
                    .stream()
                    .map(RelativeSpawner::of)
                    .toList();
            return new ElementMetadata(
                    toConnectors(
                            template.getJigsaws(BlockPos.ZERO, rotation), BlockPos.ZERO),
                    element.getBoundingBox(templateManager, BlockPos.ZERO, rotation),
                    spawners, true);
        }
        return ElementMetadata.UNSUPPORTED;
    }

    private static Connector[] toConnectors(
            List<JigsawBlockInfo> infos, BlockPos origin) {
        Connector[] connectors = new Connector[infos.size()];
        for (int index = 0; index < infos.size(); index++) {
            JigsawBlockInfo jigsaw = infos.get(index);
            StructureBlockInfo info = jigsaw.info();
            connectors[index] = new Connector(
                    info.pos().subtract(origin),
                    JigsawBlock.getFrontFacing(info.state()),
                    JigsawBlock.getTopFacing(info.state()),
                    jigsaw.pool(), jigsaw.name(), jigsaw.target(),
                    jigsaw.jointType() == JointType.ROLLABLE,
                    jigsaw.selectionPriority(), jigsaw.placementPriority());
        }
        return connectors;
    }

    private static int throwUnsupportedTerrainHeight(String reason) {
        throw new IllegalStateException(
                "试炼密室只使用 RIGID 投影，不应触发地形高度查询: " + reason);
    }

    public record Prediction(
            BlockPoint position, boolean exists,
            List<SpawnerPoint> theoreticalSpawners,
            List<SpawnerInfo> spawnerInfos,
            List<VaultInfo> vaults) {
        public Prediction(BlockPoint position, boolean exists,
                          List<SpawnerPoint> theoreticalSpawners) {
            this(position, exists, theoreticalSpawners, List.of(), List.of());
        }

        public Prediction(BlockPoint position, boolean exists,
                          List<SpawnerPoint> theoreticalSpawners,
                          List<SpawnerInfo> spawnerInfos) {
            this(position, exists, theoreticalSpawners, spawnerInfos, List.of());
        }

        public Prediction {
            theoreticalSpawners = List.copyOf(theoreticalSpawners);
            spawnerInfos = List.copyOf(spawnerInfos);
            vaults = List.copyOf(vaults);
        }

        public List<SpawnerPoint> actualSpawners() {
            return exists ? theoreticalSpawners : List.of();
        }
    }

    /** 一个试炼刷怪笼：坐标 + 模板 NBT 里的 normal/ominous 配置 id（用于解析刷怪参数）。 */
    public record SpawnerInfo(
            int x, int y, int z, String normalConfig, String ominousConfig) {
    }

    /** 一个宝库方块：坐标 + 是否不祥（ominous）变体。 */
    public record VaultInfo(int x, int y, int z, boolean ominous) {
    }

    private record StartLayout(LightPiece piece, BlockPos biomePosition) {
    }

    private record LightPiece(
            StructurePoolElement element,
            BlockPos position,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox box) {
    }

    private record QueuedPiece(LightPiece piece, FreeSpace shape, int depth) {
    }

    private record ElementMetadata(
            Connector[] connectors,
            BoundingBox boundingBox,
            List<RelativeSpawner> spawners,
            boolean supported) {
        private static final ElementMetadata UNSUPPORTED =
                new ElementMetadata(new Connector[0], null, List.of(), false);
    }

    /** 模板内相对坐标的刷怪笼：坐标 + 方块 NBT（含 normal_config / ominous_config）。 */
    private record RelativeSpawner(BlockPos pos, String normalConfig, String ominousConfig) {
        static RelativeSpawner of(StructureBlockInfo info) {
            String normal = "";
            String ominous = "";
            if (info.nbt() != null) {
                normal = info.nbt().getString("normal_config");
                ominous = info.nbt().getString("ominous_config");
            }
            return new RelativeSpawner(info.pos(), normal, ominous);
        }
    }

    record Connector(
            BlockPos relativePos,
            Direction front,
            Direction top,
            ResourceKey<StructureTemplatePool> pool,
            Identifier name,
            Identifier target,
            boolean rollable,
            int selectionPriority,
            int placementPriority) {
    }

    static final class NonAtomicLegacyRandomSource extends LegacyRandomSource {
        private final SingleThreadedRandomSource delegate;

        NonAtomicLegacyRandomSource(long seed) {
            super(0L);
            delegate = new SingleThreadedRandomSource(seed);
        }

        @Override
        public void setSeed(long seed) {
            if (delegate != null) {
                delegate.setSeed(seed);
            }
        }

        @Override
        public int next(int bits) {
            return delegate.next(bits);
        }

        @Override
        public double nextGaussian() {
            return delegate.nextGaussian();
        }
    }

    private static final class ConnectorBuffer {
        private Connector[] values = new Connector[16];
        private int size;

        private void copyFrom(Connector[] source) {
            if (values.length < source.length) {
                values = new Connector[Math.max(source.length, values.length * 2)];
            }
            System.arraycopy(source, 0, values, 0, source.length);
            size = source.length;
        }

        private int size() {
            return size;
        }

        private Connector get(int index) {
            return values[index];
        }
    }
}
