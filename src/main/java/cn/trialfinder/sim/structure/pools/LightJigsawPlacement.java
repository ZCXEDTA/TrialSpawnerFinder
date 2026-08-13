package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.resources.Identifier;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.BoundingBox;
import cn.trialfinder.sim.structure.Direction;
import cn.trialfinder.sim.structure.GenerationContext;
import cn.trialfinder.sim.structure.JigsawBlock;
import cn.trialfinder.sim.structure.JigsawBlockInfo;
import cn.trialfinder.sim.structure.LiquidSettings;
import cn.trialfinder.sim.structure.Rotation;
import cn.trialfinder.sim.structure.StructureBlockInfo;
import cn.trialfinder.sim.structure.StructureTemplate;
import cn.trialfinder.sim.structure.StructureTemplateManager;
import cn.trialfinder.sim.structure.VoxelShape;
import cn.trialfinder.sim.structure.pools.alias.PoolAliasLookup;
import cn.trialfinder.sim.util.SequencedPriorityIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight Jigsaw assembly for the predict-and-verify prefilter. Mirrors {@link JigsawPlacement}
 * exactly in RNG consumption (so the placed pieces are identical to a full assembly), but avoids
 * the per-chamber object allocations that dominate B-flow:
 * <ul>
 *   <li>{@link LightPiece} (5 fields) instead of {@link PoolElementStructurePiece} (which carries
 *       a {@code StructurePiece} base, a junctions list, and a template-manager reference);</li>
 *   <li>{@link ConnectorBuffer} (reused arrays) instead of a fresh {@code ArrayList} + full
 *       {@link JigsawBlockInfo} per candidate rotation;</li>
 *   <li>no {@code JigsawJunction} records (only needed for the final structure output).</li>
 * </ul>
 * The caller supplies the seed/chunk and receives the spawner-block positions, which is all the
 * predictor needs.
 */
public final class LightJigsawPlacement {
    private LightJigsawPlacement() {
    }

    /** Lightweight piece: the subset of {@link PoolElementStructurePiece} the layout needs. */
    record LightPiece(
            StructurePoolElement element,
            BlockPos position,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox box) {
    }

    /** Flattened connector data, extracted once per (element, rotation) and reused. */
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

    /** Reusable connector array: avoids a fresh ArrayList per candidate rotation. */
    private static final class ConnectorBuffer {
        private Connector[] values = new Connector[16];
        private int size;

        void copyFrom(Connector[] source) {
            if (this.values.length < source.length) {
                this.values = new Connector[Math.max(source.length, this.values.length * 2)];
            }
            System.arraycopy(source, 0, this.values, 0, source.length);
            this.size = source.length;
        }

        int size() {
            return this.size;
        }

        Connector get(int index) {
            return this.values[index];
        }
    }

    /**
     * Runs the lightweight assembly and returns the placed spawner positions, or empty when the
     * structure is rejected (start too close to world height limits). RNG consumption is identical
     * to {@link JigsawPlacement#addPieces} for trial chambers.
     */
    public static Optional<List<BlockPos>> predictSpawners(
            GenerationContext context,
            Holder<StructureTemplatePool> startPoolHolder,
            int size,
            BlockPos startPos,
            MaxDistance maxDistance,
            PoolAliasLookup poolAliasLookup,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings) {

        StructureTemplateManager templateManager = context.templateManager();
        PoolRegistry pools = context.pools();
        RandomSource random = context.random();
        GenerationContext.HeightAccessor heightAccessor = context.heightAccessor();

        Rotation rotation = Rotation.getRandom(random);

        ResourceKey<StructureTemplatePool> startKey = startPoolHolder.unwrapKey();
        StructureTemplatePool startPool;
        if (startKey != null) {
            ResourceKey<StructureTemplatePool> resolved = poolAliasLookup.lookup(startKey);
            startPool = pools.get(resolved).orElse(startPoolHolder.value());
        } else {
            startPool = startPoolHolder.value();
        }
        StructurePoolElement startElement = startPool.getRandomTemplate(random);
        if (startElement == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }

        // No start_jigsaw_name for trial chambers: blockPos2 = startPos.
        Vec3i vec3i = startPos.subtract(startPos);
        BlockPos blockPos3 = startPos.subtract(vec3i);

        LightPiece startPiece = new LightPiece(
                startElement,
                blockPos3,
                startElement.getGroundLevelDelta(),
                rotation,
                startElement.getBoundingBox(templateManager, blockPos3, rotation));
        BoundingBox box = startPiece.box();
        int centerX = (box.maxX() + box.minX()) / 2;
        int centerZ = (box.maxZ() + box.minZ()) / 2;
        int y = blockPos3.getY();
        int m = box.minY() + startPiece.groundLevelDelta();
        // Mirror PoolElementStructurePiece.move(0, y-m, 0): both position and box shift down.
        startPiece = new LightPiece(startElement, blockPos3.offset(0, y - m, 0),
                startElement.getGroundLevelDelta(), rotation, box.moved(0, y - m, 0));

        BoundingBox startBox = startPiece.box();
        if (isStartTooCloseToWorldHeightLimits(heightAccessor, dimensionPadding, startBox)) {
            return Optional.empty();
        }

        int n = y + vec3i.getY();
        List<LightPiece> pieces = new ArrayList<>();
        pieces.add(startPiece);
        List<BlockPos> spawners = new ArrayList<>();
        int maxDepth = size;

        if (size > 0) {
            int regionMinY = Math.max(n - maxDistance.vertical(), heightAccessor.getMinY() + dimensionPadding.bottom());
            int regionMaxY = Math.min(n + maxDistance.vertical(), heightAccessor.getMaxY() - dimensionPadding.top());
            BoundingBox region = new BoundingBox(
                    centerX - maxDistance.horizontal(), regionMinY, centerZ - maxDistance.horizontal(),
                    centerX + maxDistance.horizontal(), regionMaxY, centerZ + maxDistance.horizontal());
            VoxelShape free = VoxelShape.create(region);
            free.subtract(startBox);
            Placer placer = new Placer(pools, maxDepth, templateManager, random, free);
            placer.tryPlacingChildren(startPiece, free, 0, false, heightAccessor, poolAliasLookup, pieces, spawners);
            while (placer.placing.hasNext()) {
                QueuedPiece queued = placer.placing.next();
                placer.tryPlacingChildren(queued.piece(), queued.free(), queued.depth(),
                        false, heightAccessor, poolAliasLookup, pieces, spawners);
            }
        }
        return Optional.of(spawners);
    }

    private record QueuedPiece(LightPiece piece, VoxelShape free, int depth) {
    }

    private static boolean isStartTooCloseToWorldHeightLimits(
            GenerationContext.HeightAccessor heightAccessor, DimensionPadding dimensionPadding, BoundingBox box) {
        if (dimensionPadding == DimensionPadding.ZERO) {
            return false;
        }
        int minY = heightAccessor.getMinY() + dimensionPadding.bottom();
        int maxY = heightAccessor.getMaxY() - dimensionPadding.top();
        return box.minY() < minY || box.maxY() > maxY;
    }

    /** Per-thread reusable connector buffers + element metadata cache. */
    private static final class Placer {
        private final PoolRegistry pools;
        private final int maxDepth;
        private final StructureTemplateManager templateManager;
        private final RandomSource random;
        private final ConnectorBuffer sourceConnectors = new ConnectorBuffer();
        private final ConnectorBuffer targetConnectors = new ConnectorBuffer();
        final SequencedPriorityIterator<QueuedPiece> placing = new SequencedPriorityIterator<>();
        /** (element, rotation) -> flattened connectors + spawners, computed once per template. */
        private final java.util.concurrent.ConcurrentHashMap<Object, ElementMetadata> metadataCache =
                new java.util.concurrent.ConcurrentHashMap<>();

        Placer(PoolRegistry pools, int maxDepth, StructureTemplateManager templateManager,
               RandomSource random, VoxelShape free) {
            this.pools = pools;
            this.maxDepth = maxDepth;
            this.templateManager = templateManager;
            this.random = random;
        }

        void tryPlacingChildren(
                LightPiece source,
                VoxelShape contextFree,
                int depth,
                boolean useExpansionHack,
                GenerationContext.HeightAccessor heightAccessor,
                PoolAliasLookup poolAliasLookup,
                List<LightPiece> pieces,
                List<BlockPos> spawners) {
            StructurePoolElement sourceElement = source.element();
            BlockPos sourcePos = source.position();
            Rotation sourceRotation = source.rotation();
            Projection sourceProjection = sourceElement.getProjection();
            boolean sourceRigid = sourceProjection == Projection.RIGID;
            VoxelShape sourceFree = null;
            BoundingBox sourceBox = source.box();
            int sourceMinY = sourceBox.minY();

            fillConnectors(sourceElement, sourcePos, sourceRotation, random, sourceConnectors);
            int size = sourceConnectors.size();
            outer:
            for (int sourceIndex = 0; sourceIndex < size; sourceIndex++) {
                Connector sourceConnector = sourceConnectors.get(sourceIndex);
                Direction sourceDirection = sourceConnector.front();
                BlockPos sourceJigsawPos = sourcePos.offset(sourceConnector.relativePos());
                BlockPos connectPos = sourceJigsawPos.offset(sourceDirection);
                int k = sourceJigsawPos.getY() - sourceMinY;
                int l = Integer.MIN_VALUE;

                ResourceKey<StructureTemplatePool> poolKey = poolAliasLookup.lookup(sourceConnector.pool());
                Optional<StructureTemplatePool> poolOptional = this.pools.get(poolKey);
                if (poolOptional.isEmpty()) {
                    continue;
                }
                StructureTemplatePool pool = poolOptional.get();
                if (pool.size() == 0 && !this.pools.isEmptyPool(pool)) {
                    continue;
                }
                StructureTemplatePool fallbackPool = pool.getFallback().value();
                if (fallbackPool.size() == 0 && !this.pools.isEmptyPool(fallbackPool)) {
                    continue;
                }

                boolean connectInside = sourceBox.isInside(connectPos);
                VoxelShape freeShape;
                if (connectInside) {
                    if (sourceFree == null) {
                        sourceFree = VoxelShape.create(sourceBox);
                    }
                    freeShape = sourceFree;
                } else {
                    freeShape = contextFree;
                }

                List<StructurePoolElement> candidates = new ArrayList<>();
                if (depth != this.maxDepth) {
                    candidates.addAll(pool.getShuffledTemplates(this.random));
                }
                candidates.addAll(fallbackPool.getShuffledTemplates(this.random));
                int priority = sourceConnector.placementPriority();

                for (StructurePoolElement candidate : candidates) {
                    if (candidate == EmptyPoolElement.INSTANCE) {
                        break;
                    }
                    for (Rotation candidateRotation : Rotation.getShuffled(this.random)) {
                        fillConnectors(candidate, BlockPos.ZERO, candidateRotation, random, targetConnectors);
                        BoundingBox candidateBox = candidate.getBoundingBox(
                                this.templateManager, BlockPos.ZERO, candidateRotation);
                        int n = 0;
                        int tConnectorSize = targetConnectors.size();
                        for (int targetIndex = 0; targetIndex < tConnectorSize; targetIndex++) {
                            Connector targetConnector = targetConnectors.get(targetIndex);
                            if (!canAttach(sourceConnector, targetConnector)) {
                                continue;
                            }
                            BlockPos candidateJigsawPos = targetConnector.relativePos();
                            BlockPos placementPos = connectPos.subtract(candidateJigsawPos);
                            BoundingBox placementBox = candidate.getBoundingBox(
                                    this.templateManager, placementPos, candidateRotation);
                            int o = placementBox.minY();
                            Projection candidateProjection = candidate.getProjection();
                            boolean candidateRigid = candidateProjection == Projection.RIGID;
                            int p = candidateJigsawPos.getY();
                            int q = k - p + sourceDirection.getStepY();
                            int r;
                            if (sourceRigid && candidateRigid) {
                                r = sourceMinY + q;
                            } else {
                                if (l == Integer.MIN_VALUE) {
                                    throw new IllegalStateException(
                                            "Terrain height lookup is not supported; trial chambers use RIGID projection");
                                }
                                r = l - p;
                            }
                            int s = r - o;
                            BoundingBox movedBox = placementBox.moved(0, s, 0);
                            BlockPos movedPos = placementPos.offset(0, s, 0);

                            if (freeShape.joinIsNotEmpty(movedBox)) {
                                continue;
                            }
                            freeShape.subtract(movedBox);

                            int t = source.groundLevelDelta();
                            int u;
                            if (candidateRigid) {
                                u = t - q;
                            } else {
                                u = candidate.getGroundLevelDelta();
                            }
                            LightPiece child = new LightPiece(
                                    candidate, movedPos, u, candidateRotation, movedBox);
                            // Collect spawner blocks from the child's template (rotated, offset).
                            addSpawners(child, spawners);
                            if (depth + 1 <= this.maxDepth) {
                                this.placing.add(new QueuedPiece(child, freeShape, depth + 1), priority);
                            }
                            continue outer;
                        }
                    }
                }
            }
        }

        private void addSpawners(LightPiece piece, List<BlockPos> spawners) {
            if (!(piece.element() instanceof SinglePoolElement single)) {
                return;
            }
            StructureTemplate template = single.getTemplate(this.templateManager);
            BlockPos pos = piece.position();
            for (StructureBlockInfo info : template.getSpawnerBlocks(piece.rotation())) {
                spawners.add(info.pos().offset(pos));
            }
        }

        private void fillConnectors(
                StructurePoolElement element, BlockPos position,
                Rotation rotation, RandomSource random, ConnectorBuffer output) {
            ElementMetadata metadata = metadata(element, rotation);
            output.copyFrom(metadata.connectors());
            orderConnectors(output.values, output.size, random);
        }

        private ElementMetadata metadata(StructurePoolElement element, Rotation rotation) {
            Object key = new ElementKey(element, rotation);
            ElementMetadata cached = metadataCache.get(key);
            if (cached != null) {
                return cached;
            }
            ElementMetadata computed = createMetadata(element, rotation);
            ElementMetadata existing = metadataCache.putIfAbsent(key, computed);
            return existing != null ? existing : computed;
        }

        private ElementMetadata createMetadata(StructurePoolElement element, Rotation rotation) {
            if (element instanceof SinglePoolElement single) {
                StructureTemplate template = single.getTemplate(this.templateManager);
                List<JigsawBlockInfo> jigsaws = template.getJigsaws(BlockPos.ZERO, rotation);
                Connector[] connectors = new Connector[jigsaws.size()];
                for (int i = 0; i < jigsaws.size(); i++) {
                    JigsawBlockInfo jigsaw = jigsaws.get(i);
                    StructureBlockInfo info = jigsaw.info();
                    connectors[i] = new Connector(
                            info.pos(),
                            JigsawBlock.getFrontFacing(info.state()),
                            JigsawBlock.getTopFacing(info.state()),
                            jigsaw.pool(), jigsaw.name(), jigsaw.target(),
                            jigsaw.jointType() == cn.trialfinder.sim.structure.JointType.ROLLABLE,
                            jigsaw.selectionPriority(), jigsaw.placementPriority());
                }
                List<BlockPos> spawners = new ArrayList<>();
                for (StructureBlockInfo s : template.getSpawnerBlocks(rotation)) {
                    spawners.add(s.pos());
                }
                return new ElementMetadata(connectors, spawners);
            }
            // Unsupported element (list/legacy): fall back to per-call extraction.
            List<JigsawBlockInfo> jigsaws = element.getShuffledJigsawBlocks(
                    this.templateManager, BlockPos.ZERO, rotation, this.random);
            Connector[] connectors = new Connector[jigsaws.size()];
            for (int i = 0; i < jigsaws.size(); i++) {
                JigsawBlockInfo jigsaw = jigsaws.get(i);
                StructureBlockInfo info = jigsaw.info();
                connectors[i] = new Connector(
                        info.pos(),
                        JigsawBlock.getFrontFacing(info.state()),
                        JigsawBlock.getTopFacing(info.state()),
                        jigsaw.pool(), jigsaw.name(), jigsaw.target(),
                        jigsaw.jointType() == cn.trialfinder.sim.structure.JointType.ROLLABLE,
                        jigsaw.selectionPriority(), jigsaw.placementPriority());
            }
            return new ElementMetadata(connectors, List.of());
        }

        private static void orderConnectors(Connector[] connectors, int size, RandomSource random) {
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
                        && connectors[insertion - 1].selectionPriority() < current.selectionPriority()) {
                    connectors[insertion] = connectors[insertion - 1];
                    insertion--;
                }
                connectors[insertion] = current;
            }
        }

        private static boolean canAttach(Connector source, Connector target) {
            boolean rollable = source.rollable();
            return source.front() == target.front().getOpposite()
                    && (rollable || source.top() == target.top())
                    && source.target().equals(target.name());
        }
    }

    /** Cached per-(element, rotation) connectors and spawners. */
    private record ElementMetadata(Connector[] connectors, List<BlockPos> spawners) {
    }

    /** Equality key for the metadata cache (element + rotation). */
    private record ElementKey(StructurePoolElement element, Rotation rotation) {
    }
}
