package cn.trialfinder.world;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.mixin.JigsawStructureAccessor;
import cn.trialfinder.model.SpawnerPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SequencedPriorityIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight 26.2 trial-chamber layout calculator. Random calls and placement
 * order mirror {@code JigsawPlacement}; immutable template metadata is cached.
 */
public final class HandTrialChamberPredictor {
    private static final Rotation[] ROTATIONS = Rotation.values();
    private static final ConcurrentMap<StructurePoolElement, ElementMetadata[]> METADATA_CACHE =
            new ConcurrentHashMap<>();

    private final ServerLevel world;
    private final JigsawStructure structure;
    private final JigsawStructureAccessor settings;
    private final ChunkGenerator chunkGenerator;
    private final StructureTemplateManager templateManager;
    private final RandomState randomState;
    private final Registry<StructureTemplatePool> pools;
    private final WorldgenRandom layoutRandom =
            new WorldgenRandom(new NonAtomicLegacyRandomSource(0L));
    // Children are queued instead of generated recursively, so source and target are the only live connector sets.
    private final ConnectorBuffer sourceConnectorBuffer = new ConnectorBuffer();
    private final ConnectorBuffer targetConnectorBuffer = new ConnectorBuffer();
    private final IdentityHashMap<StructurePoolElement, ElementMetadata[]> localMetadata =
            new IdentityHashMap<>();
    private final int maxDepth;
    private final boolean useExpansionHack;

    public HandTrialChamberPredictor(ServerLevel world) {
        this.world = world;
        Structure registered = world.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(Identifier.fromNamespaceAndPath("minecraft", "trial_chambers"));
        if (!(registered instanceof JigsawStructure jigsaw)) {
            throw new IllegalStateException(
                    "Minecraft registry entry trial_chambers is not a Jigsaw structure");
        }
        this.structure = jigsaw;
        this.settings = (JigsawStructureAccessor) (Object) jigsaw;
        this.chunkGenerator = world.getChunkSource().getGenerator();
        this.templateManager = world.getStructureManager();
        this.randomState = world.getChunkSource().randomState();
        this.pools = world.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        this.maxDepth = settings.trialFinder$getSize();
        this.useExpansionHack = settings.trialFinder$getUseExpansionHack();
    }

    public Prediction predict(BlockPoint candidate) {
        ChunkPos chunkPos = new ChunkPos(
                Math.floorDiv(candidate.x(), 16), Math.floorDiv(candidate.z(), 16));
        WorldgenRandom random = layoutRandom;
        random.setLargeFeatureSeed(world.getSeed(), chunkPos.x(), chunkPos.z());
        int startY = settings.trialFinder$getStartHeight().sample(
                random, new WorldGenerationContext(chunkGenerator, world));
        BlockPos start = new BlockPos(
                chunkPos.getMinBlockX(), startY, chunkPos.getMinBlockZ());
        PoolAliasLookup aliases = PoolAliasLookup.create(
                structure.getPoolAliases(), start, world.getSeed());
        StartLayout layout = createStart(start, random, aliases);
        if (layout == null || !isValidBiome(layout.biomePosition())) {
            return new Prediction(candidate, false, List.of());
        }

        List<LightPiece> pieces = new ArrayList<>();
        pieces.add(layout.piece());
        if (maxDepth > 0) {
            generateChildren(layout, pieces, random, aliases);
        }

        Set<SpawnerPoint> spawners = new HashSet<>();
        for (LightPiece piece : pieces) {
            collectSpawners(piece, spawners);
        }
        List<SpawnerPoint> sorted = new ArrayList<>(spawners);
        sorted.sort(SpawnerPoint::compareTo);
        return new Prediction(candidate, !sorted.isEmpty(), sorted);
    }

    private StartLayout createStart(
            BlockPos start, WorldgenRandom random, PoolAliasLookup aliases) {
        Rotation rotation = Rotation.getRandom(random);
        Holder<StructureTemplatePool> configuredPool = structure.getStartPool();
        StructureTemplatePool startPool = configuredPool.unwrapKey()
                .flatMap(key -> pools.getOptional(aliases.lookup(key)))
                .orElse(configuredPool.value());
        StructurePoolElement element = startPool.getRandomTemplate(random);
        if (element == EmptyPoolElement.INSTANCE) {
            return null;
        }

        BlockPos anchoredPosition = start;
        Optional<Identifier> startJigsawName = settings.trialFinder$getStartJigsawName();
        if (startJigsawName.isPresent()) {
            anchoredPosition = findStartingJigsaw(
                    element, startJigsawName.get(), start, rotation, random).orElse(null);
            if (anchoredPosition == null) {
                return null;
            }
        }

        Vec3i localAnchor = anchoredPosition.subtract(start);
        BlockPos piecePosition = start.subtract(localAnchor);
        BoundingBox box = boundingBox(element, piecePosition, rotation);
        int centerX = (box.maxX() + box.minX()) / 2;
        int centerZ = (box.maxZ() + box.minZ()) / 2;
        int groundY = settings.trialFinder$getProjectStartToHeightmap().isEmpty()
                ? piecePosition.getY()
                : start.getY() + chunkGenerator.getFirstFreeHeight(
                        centerX, centerZ,
                        settings.trialFinder$getProjectStartToHeightmap().orElseThrow(),
                        world, randomState);
        int verticalOffset = groundY - (box.minY() + element.getGroundLevelDelta());
        piecePosition = piecePosition.offset(0, verticalOffset, 0);
        box = box.moved(0, verticalOffset, 0);

        DimensionPadding padding = settings.trialFinder$getDimensionPadding();
        if (padding != DimensionPadding.ZERO
                && (box.minY() < world.getMinY() + padding.bottom()
                || box.maxY() > world.getMaxY() - padding.top())) {
            return null;
        }

        LightPiece piece = new LightPiece(
                element, piecePosition, element.getGroundLevelDelta(), rotation, box);
        return new StartLayout(
                piece, new BlockPos(centerX, groundY + localAnchor.getY(), centerZ));
    }

    private boolean isValidBiome(BlockPos position) {
        return structure.biomes().contains(
                chunkGenerator.getBiomeSource().getNoiseBiome(
                        QuartPos.fromBlock(position.getX()),
                        QuartPos.fromBlock(position.getY()),
                        QuartPos.fromBlock(position.getZ()),
                        randomState.sampler()));
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
        JigsawStructure.MaxDistance distance =
                settings.trialFinder$getMaxDistanceFromCenter();
        DimensionPadding padding = settings.trialFinder$getDimensionPadding();
        AABB allowed = new AABB(
                center.getX() - distance.horizontal(),
                Math.max(center.getY() - distance.vertical(),
                        world.getMinY() + padding.bottom()),
                center.getZ() - distance.horizontal(),
                center.getX() + distance.horizontal() + 1,
                Math.min(center.getY() + distance.vertical() + 1,
                        world.getMaxY() + 1 - padding.top()),
                center.getZ() + distance.horizontal() + 1);
        FreeSpace shape = new FreeSpace(allowed, first.box());
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
                == StructureTemplatePool.Projection.RIGID;
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

            ResourceKey<StructureTemplatePool> poolKey =
                    aliases.lookup(sourceConnector.pool());
            Optional<? extends Holder<StructureTemplatePool>> selectedEntry = pools.get(poolKey);
            if (selectedEntry.isEmpty()) {
                continue;
            }
            StructureTemplatePool selectedPool = selectedEntry.get().value();
            StructureTemplatePool fallbackPool = selectedPool.getFallback().value();

            boolean attachInsideSource = source.box().isInside(targetJigsawPos);
            FreeSpace childrenFree;
            if (attachInsideSource) {
                if (sourceFree == null) {
                    sourceFree = new FreeSpace(AABB.of(source.box()), null);
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
                                == StructureTemplatePool.Projection.RIGID;
                        int targetJigsawLocalY = targetJigsawLocalPos.getY();
                        int deltaY = sourceJigsawLocalY - targetJigsawLocalY
                                + sourceDirection.getStepY();
                        int targetBoxY;
                        if (sourceRigid && targetRigid) {
                            targetBoxY = sourceBoxY + deltaY;
                        } else {
                            if (sourceJigsawBaseHeight == Integer.MIN_VALUE) {
                                sourceJigsawBaseHeight = chunkGenerator.getFirstFreeHeight(
                                        sourceJigsawPos.getX(), sourceJigsawPos.getZ(),
                                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                                        world, randomState);
                            }
                            targetBoxY = sourceJigsawBaseHeight - targetJigsawLocalY;
                        }

                        int verticalOffset = targetBoxY - rawTargetY;
                        BoundingBox targetBox = rawTargetBox.moved(0, verticalOffset, 0);
                        BlockPos targetPosition =
                                rawTargetPosition.offset(0, verticalOffset, 0);
                        if (expandTo > 0) {
                            int newSize = Math.max(
                                    expandTo + 1, targetBox.maxY() - targetBox.minY());
                            targetBox.encapsulate(new BlockPos(
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
            Optional<? extends Holder<StructureTemplatePool>> pool = pools.get(key);
            if (pool.isEmpty()) {
                continue;
            }
            StructureTemplatePool value = pool.get().value();
            largest = Math.max(largest, value.getMaxSize(templateManager));
            largest = Math.max(
                    largest, value.getFallback().value().getMaxSize(templateManager));
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

    private void collectSpawners(LightPiece piece, Set<SpawnerPoint> output) {
        ElementMetadata metadata = metadata(piece.element(), piece.rotation());
        if (metadata.supported()) {
            for (BlockPos relative : metadata.spawners()) {
                output.add(new SpawnerPoint(
                        piece.position().getX() + relative.getX(),
                        piece.position().getY() + relative.getY(),
                        piece.position().getZ() + relative.getZ()));
            }
            return;
        }
        collectSpawnersSlow(
                piece.element(), piece.position(), piece.rotation(), output);
    }

    private void collectSpawnersSlow(
            StructurePoolElement element, BlockPos position,
            Rotation rotation, Set<SpawnerPoint> output) {
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : list.getElements()) {
                collectSpawnersSlow(child, position, rotation, output);
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
        for (StructureTemplate.StructureBlockInfo block :
                template.filterBlocks(position, placement, Blocks.TRIAL_SPAWNER)) {
            output.add(new SpawnerPoint(
                    block.pos().getX(), block.pos().getY(), block.pos().getZ()));
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
            List<BlockPos> spawners = new ArrayList<>();
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
            StructurePlaceSettings placement =
                    new StructurePlaceSettings().setRotation(rotation);
            List<BlockPos> spawners = template.filterBlocks(
                            BlockPos.ZERO, placement, Blocks.TRIAL_SPAWNER)
                    .stream()
                    .map(StructureTemplate.StructureBlockInfo::pos)
                    .toList();
            return new ElementMetadata(
                    toConnectors(template.getJigsaws(BlockPos.ZERO, rotation), BlockPos.ZERO),
                    element.getBoundingBox(templateManager, BlockPos.ZERO, rotation),
                    spawners, true);
        }
        return ElementMetadata.UNSUPPORTED;
    }

    private static Connector[] toConnectors(
            List<StructureTemplate.JigsawBlockInfo> infos, BlockPos origin) {
        Connector[] connectors = new Connector[infos.size()];
        for (int index = 0; index < infos.size(); index++) {
            StructureTemplate.JigsawBlockInfo jigsaw = infos.get(index);
            var info = jigsaw.info();
            connectors[index] = new Connector(
                    info.pos().subtract(origin),
                    JigsawBlock.getFrontFacing(info.state()),
                    JigsawBlock.getTopFacing(info.state()),
                    jigsaw.pool(), jigsaw.name(), jigsaw.target(),
                    jigsaw.jointType()
                            == net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType.ROLLABLE,
                    jigsaw.selectionPriority(), jigsaw.placementPriority());
        }
        return connectors;
    }

    public record Prediction(
            BlockPoint position, boolean exists,
            List<SpawnerPoint> theoreticalSpawners) {
        public Prediction {
            theoreticalSpawners = List.copyOf(theoreticalSpawners);
        }

        public List<SpawnerPoint> actualSpawners() {
            return exists ? theoreticalSpawners : List.of();
        }
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
            List<BlockPos> spawners,
            boolean supported) {
        private static final ElementMetadata UNSUPPORTED =
                new ElementMetadata(new Connector[0], null, List.of(), false);
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

    private static final class FreeSpace {
        private static final int CELL_SIZE = 16;
        private static final int AUDIT_SPACE_LIMIT = 1_024;
        private static final AtomicInteger AUDITED_SPACES = new AtomicInteger();
        private static volatile boolean auditBudgetExhausted;

        private final AABB bounds;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private final int cellsX;
        private final int cellsY;
        private final int cellsZ;
        private final List<BlockedBox> blocked = new ArrayList<>();
        private final List<BlockedBox>[] cells;
        private VoxelShape auditShape;
        private int queryId;

        @SuppressWarnings("unchecked")
        private FreeSpace(AABB bounds, BoundingBox initiallyBlocked) {
            this.bounds = bounds;
            this.baseX = (int) Math.floor(bounds.minX);
            this.baseY = (int) Math.floor(bounds.minY);
            this.baseZ = (int) Math.floor(bounds.minZ);
            this.cellsX = Math.max(
                    1, Math.ceilDiv((int) Math.ceil(bounds.maxX) - baseX, CELL_SIZE));
            this.cellsY = Math.max(
                    1, Math.ceilDiv((int) Math.ceil(bounds.maxY) - baseY, CELL_SIZE));
            this.cellsZ = Math.max(
                    1, Math.ceilDiv((int) Math.ceil(bounds.maxZ) - baseZ, CELL_SIZE));
            this.cells = initiallyBlocked == null
                    ? null
                    : (List<BlockedBox>[]) new List<?>[cellsX * cellsY * cellsZ];
            if (takeAuditSlot()) {
                auditShape = initiallyBlocked == null
                        ? Shapes.create(bounds)
                        : Shapes.join(
                                Shapes.create(bounds),
                                Shapes.create(AABB.of(initiallyBlocked)),
                                BooleanOp.ONLY_FIRST);
            }
            if (initiallyBlocked != null) {
                add(initiallyBlocked);
            }
        }

        private static boolean takeAuditSlot() {
            if (auditBudgetExhausted) {
                return false;
            }
            int slot = AUDITED_SPACES.getAndIncrement();
            if (slot < AUDIT_SPACE_LIMIT) {
                return true;
            }
            auditBudgetExhausted = true;
            return false;
        }

        private boolean contains(BoundingBox candidate) {
            boolean fast = containsFast(candidate);
            if (auditShape != null) {
                boolean vanilla = !Shapes.joinIsNotEmpty(
                        auditShape,
                        Shapes.create(AABB.of(candidate).deflate(0.25)),
                        BooleanOp.ONLY_SECOND);
                if (fast != vanilla) {
                    throw new IllegalStateException(
                            "Fast collision check differs from vanilla VoxelShape");
                }
            }
            return fast;
        }

        private void occupy(BoundingBox box) {
            add(box);
            if (auditShape != null) {
                auditShape = Shapes.joinUnoptimized(
                        auditShape, Shapes.create(AABB.of(box)), BooleanOp.ONLY_FIRST);
            }
        }

        private boolean containsFast(BoundingBox candidate) {
            if (candidate.minX() + 0.25 < bounds.minX
                    || candidate.maxX() + 0.75 > bounds.maxX
                    || candidate.minY() + 0.25 < bounds.minY
                    || candidate.maxY() + 0.75 > bounds.maxY
                    || candidate.minZ() + 0.25 < bounds.minZ
                    || candidate.maxZ() + 0.75 > bounds.maxZ) {
                return false;
            }
            if (cells == null) {
                for (BlockedBox entry : blocked) {
                    if (intersects(candidate, entry.box)) {
                        return false;
                    }
                }
                return true;
            }

            int currentQuery = ++queryId;
            for (int x = cellX(candidate.minX()); x <= cellX(candidate.maxX()); x++) {
                for (int y = cellY(candidate.minY()); y <= cellY(candidate.maxY()); y++) {
                    for (int z = cellZ(candidate.minZ()); z <= cellZ(candidate.maxZ()); z++) {
                        List<BlockedBox> entries = cells[index(x, y, z)];
                        if (entries == null) {
                            continue;
                        }
                        for (BlockedBox entry : entries) {
                            if (entry.lastQuery == currentQuery) {
                                continue;
                            }
                            entry.lastQuery = currentQuery;
                            if (intersects(candidate, entry.box)) {
                                return false;
                            }
                        }
                    }
                }
            }
            return true;
        }

        private void add(BoundingBox box) {
            BlockedBox entry = new BlockedBox(box);
            blocked.add(entry);
            if (cells == null) {
                return;
            }
            for (int x = cellX(box.minX()); x <= cellX(box.maxX()); x++) {
                for (int y = cellY(box.minY()); y <= cellY(box.maxY()); y++) {
                    for (int z = cellZ(box.minZ()); z <= cellZ(box.maxZ()); z++) {
                        int index = index(x, y, z);
                        List<BlockedBox> entries = cells[index];
                        if (entries == null) {
                            entries = new ArrayList<>();
                            cells[index] = entries;
                        }
                        entries.add(entry);
                    }
                }
            }
        }

        private int cellX(int x) {
            return Math.max(
                    0, Math.min(cellsX - 1, Math.floorDiv(x - baseX, CELL_SIZE)));
        }

        private int cellY(int y) {
            return Math.max(
                    0, Math.min(cellsY - 1, Math.floorDiv(y - baseY, CELL_SIZE)));
        }

        private int cellZ(int z) {
            return Math.max(
                    0, Math.min(cellsZ - 1, Math.floorDiv(z - baseZ, CELL_SIZE)));
        }

        private int index(int x, int y, int z) {
            return (x * cellsY + y) * cellsZ + z;
        }

        private static boolean intersects(BoundingBox first, BoundingBox second) {
            return first.maxX() >= second.minX() && first.minX() <= second.maxX()
                    && first.maxY() >= second.minY() && first.minY() <= second.maxY()
                    && first.maxZ() >= second.minZ() && first.minZ() <= second.maxZ();
        }

        private static final class BlockedBox {
            private final BoundingBox box;
            private int lastQuery;

            private BlockedBox(BoundingBox box) {
                this.box = box;
            }
        }
    }
}
