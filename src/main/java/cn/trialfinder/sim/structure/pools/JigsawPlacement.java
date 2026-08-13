package cn.trialfinder.sim.structure.pools;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.random.RandomSource;
import cn.trialfinder.sim.resources.Holder;
import cn.trialfinder.sim.resources.ResourceKey;
import cn.trialfinder.sim.structure.AABB;
import cn.trialfinder.sim.structure.BoundingBox;
import cn.trialfinder.sim.structure.Direction;
import cn.trialfinder.sim.structure.GenerationContext;
import cn.trialfinder.sim.structure.JigsawBlock;
import cn.trialfinder.sim.structure.JigsawBlockInfo;
import cn.trialfinder.sim.structure.JigsawJunction;
import cn.trialfinder.sim.structure.LiquidSettings;
import cn.trialfinder.sim.structure.Rotation;
import cn.trialfinder.sim.structure.StructureBlockInfo;
import cn.trialfinder.sim.structure.StructureTemplateManager;
import cn.trialfinder.sim.structure.VoxelShape;
import cn.trialfinder.sim.structure.pools.alias.PoolAliasLookup;
import cn.trialfinder.sim.util.SequencedPriorityIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Port of net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement (1.21.11),
 * including the inner {@link Placer}. Adapted for trial chambers:
 * <ul>
 *   <li>projection is always RIGID and {@code useExpansionHack} is false, so the terrain-height
 *       query (getFirstFreeHeight) is never reached;</li>
 *   <li>the vanilla VoxelShape boolean ops reduce to analytic integer-box collision
 *       (see {@link VoxelShape}).</li>
 * </ul>
 * RNG consumption order is preserved bit-exactly.
 */
public final class JigsawPlacement {
    private JigsawPlacement() {
    }

    /**
     * Checkpoint hook for the predict-and-verify prefilter. Called once, at the first piece of
     * depth {@code checkpointDepth} (strict BFS order), after all pieces of depth
     * {@code < checkpointDepth} have been placed. Because BFS order means the placed pieces are
     * exactly the full-depth result's depth-{@code < D} prefix (identical RNG consumption), the
     * caller may decide to stop (returning {@code false} — dropping the structure) or continue
     * (returning {@code true} — resuming with the same RNG state as a full run, so the final
     * pieces are identical to a complete assembly).
     */
    @FunctionalInterface
    public interface Checkpoint {
        boolean shouldContinue(List<PoolElementStructurePiece> placedPieces, int checkpointDepth);
    }

    /** Result of a checkpointed assembly: the pieces (when not stopped) and whether it stopped early. */
    public record CheckpointedResult(Optional<JigsawResult> result, boolean stoppedEarly) {
    }

    /**
     * Runs the full jigsaw assembly and returns the placed pieces (start piece first).
     * Returns empty if the structure is rejected (e.g. too close to world height limits).
     */
    public static Optional<JigsawResult> addPieces(
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

        PoolElementStructurePiece startPiece = new PoolElementStructurePiece(
                templateManager,
                startElement,
                blockPos3,
                startElement.getGroundLevelDelta(),
                rotation,
                startElement.getBoundingBox(templateManager, blockPos3, rotation));
        BoundingBox box = startPiece.getBoundingBox();
        int centerX = (box.maxX() + box.minX()) / 2;
        int centerZ = (box.maxZ() + box.minZ()) / 2;
        // projectStartToHeightmap is empty for trial chambers → use the raw start Y.
        int y = blockPos3.getY();
        int m = box.minY() + startPiece.getGroundLevelDelta();
        startPiece.move(0, y - m, 0);

        if (isStartTooCloseToWorldHeightLimits(heightAccessor, dimensionPadding, startPiece.getBoundingBox())) {
            return Optional.empty();
        }

        int n = y + vec3i.getY();
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        pieces.add(startPiece);
        if (size > 0) {
            int regionMinY = Math.max(n - maxDistance.vertical(), heightAccessor.getMinY() + dimensionPadding.bottom());
            int regionMaxY = Math.min(n + maxDistance.vertical(), heightAccessor.getMaxY() - dimensionPadding.top());
            BoundingBox region = new BoundingBox(
                    centerX - maxDistance.horizontal(), regionMinY, centerZ - maxDistance.horizontal(),
                    centerX + maxDistance.horizontal(), regionMaxY, centerZ + maxDistance.horizontal());
            VoxelShape free = VoxelShape.create(region);
            // Vanilla BoundingBox.move mutates in place, so the free shape subtracts the POST-move
            // start box; the region center above uses the pre-move box (matches JigsawPlacement).
            free.subtract(startPiece.getBoundingBox());
            addPieces(context, size, templateManager, pools, random, startPiece, pieces, free, poolAliasLookup, liquidSettings);
        }
        return Optional.of(new JigsawResult(pieces, startPiece.getBoundingBox()));
    }

    /**
     * {@link #addPieces} with a checkpoint hook. When {@code checkpointDepth > 0}, assembly pauses
     * at the first piece of that depth (strict BFS order) and asks {@code checkpoint}; if it returns
     * {@code false}, assembly stops immediately (no further RNG is consumed) and
     * {@link CheckpointedResult#stoppedEarly()} is {@code true}. Otherwise assembly continues and the
     * result is identical to a full run. {@code checkpointDepth <= 0} (or {@code >= size}) behaves
     * exactly like {@link #addPieces} (no checkpoint fires).
     */
    public static CheckpointedResult addPiecesWithCheckpoint(
            GenerationContext context,
            Holder<StructureTemplatePool> startPoolHolder,
            int size,
            BlockPos startPos,
            MaxDistance maxDistance,
            PoolAliasLookup poolAliasLookup,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings,
            int checkpointDepth,
            Checkpoint checkpoint) {

        if (checkpointDepth <= 0 || checkpointDepth >= size || checkpoint == null) {
            return new CheckpointedResult(addPieces(context, startPoolHolder, size, startPos,
                    maxDistance, poolAliasLookup, dimensionPadding, liquidSettings), false);
        }

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
            return new CheckpointedResult(Optional.empty(), false);
        }

        Vec3i vec3i = startPos.subtract(startPos);
        BlockPos blockPos3 = startPos.subtract(vec3i);

        PoolElementStructurePiece startPiece = new PoolElementStructurePiece(
                templateManager,
                startElement,
                blockPos3,
                startElement.getGroundLevelDelta(),
                rotation,
                startElement.getBoundingBox(templateManager, blockPos3, rotation));
        BoundingBox box = startPiece.getBoundingBox();
        int centerX = (box.maxX() + box.minX()) / 2;
        int centerZ = (box.maxZ() + box.minZ()) / 2;
        int y = blockPos3.getY();
        int m = box.minY() + startPiece.getGroundLevelDelta();

        startPiece.move(0, y - m, 0);

        if (isStartTooCloseToWorldHeightLimits(heightAccessor, dimensionPadding, startPiece.getBoundingBox())) {
            return new CheckpointedResult(Optional.empty(), false);
        }

        int n = y + vec3i.getY();
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        pieces.add(startPiece);
        if (size > 0) {
            int regionMinY = Math.max(n - maxDistance.vertical(), heightAccessor.getMinY() + dimensionPadding.bottom());
            int regionMaxY = Math.min(n + maxDistance.vertical(), heightAccessor.getMaxY() - dimensionPadding.top());
            BoundingBox region = new BoundingBox(
                    centerX - maxDistance.horizontal(), regionMinY, centerZ - maxDistance.horizontal(),
                    centerX + maxDistance.horizontal(), regionMaxY, centerZ + maxDistance.horizontal());
            VoxelShape free = VoxelShape.create(region);
            free.subtract(startPiece.getBoundingBox());
            boolean stoppedEarly = addPiecesWithCheckpoint(context, size, templateManager, pools, random,
                    startPiece, pieces, free, poolAliasLookup, liquidSettings, checkpointDepth, checkpoint);
            if (stoppedEarly) {
                return new CheckpointedResult(Optional.empty(), true);
            }
        }
        return new CheckpointedResult(
                Optional.of(new JigsawResult(pieces, startPiece.getBoundingBox())), false);
    }

    /** Returns {@code true} when the assembly was stopped early at the checkpoint. */
    private static boolean addPiecesWithCheckpoint(
            GenerationContext context,
            int size,
            StructureTemplateManager templateManager,
            PoolRegistry pools,
            RandomSource random,
            PoolElementStructurePiece startPiece,
            List<PoolElementStructurePiece> pieces,
            VoxelShape free,
            PoolAliasLookup poolAliasLookup,
            LiquidSettings liquidSettings,
            int checkpointDepth,
            Checkpoint checkpoint) {
        Placer placer = new Placer(pools, size, templateManager, pieces, random);
        placer.tryPlacingChildren(startPiece, free, 0, false, context.heightAccessor(), poolAliasLookup, liquidSettings);
        boolean checkpointFired = false;
        while (placer.placing.hasNext()) {
            PieceState pieceState = placer.placing.next();
            if (!checkpointFired && pieceState.depth() == checkpointDepth) {
                checkpointFired = true;
                if (!checkpoint.shouldContinue(pieces, checkpointDepth)) {
                    return true; // stop: drop the structure
                }
            }
            placer.tryPlacingChildren(pieceState.piece(), pieceState.free(), pieceState.depth(),
                    false, context.heightAccessor(), poolAliasLookup, liquidSettings);
        }
        return false;
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

    private static void addPieces(
            GenerationContext context,
            int size,
            StructureTemplateManager templateManager,
            PoolRegistry pools,
            RandomSource random,
            PoolElementStructurePiece startPiece,
            List<PoolElementStructurePiece> pieces,
            VoxelShape free,
            PoolAliasLookup poolAliasLookup,
            LiquidSettings liquidSettings) {
        Placer placer = new Placer(pools, size, templateManager, pieces, random);
        placer.tryPlacingChildren(startPiece, free, 0, false, context.heightAccessor(), poolAliasLookup, liquidSettings);
        while (placer.placing.hasNext()) {
            PieceState pieceState = placer.placing.next();
            placer.tryPlacingChildren(pieceState.piece(), pieceState.free(), pieceState.depth(),
                    false, context.heightAccessor(), poolAliasLookup, liquidSettings);
        }
    }

    record PieceState(PoolElementStructurePiece piece, VoxelShape free, int depth) {
    }

    static final class Placer {
        private final PoolRegistry pools;
        private final int maxDepth;
        private final StructureTemplateManager structureTemplateManager;
        private final List<PoolElementStructurePiece> pieces;
        private final RandomSource random;
        final SequencedPriorityIterator<PieceState> placing = new SequencedPriorityIterator<>();

        Placer(PoolRegistry pools, int maxDepth, StructureTemplateManager structureTemplateManager,
               List<PoolElementStructurePiece> pieces, RandomSource random) {
            this.pools = pools;
            this.maxDepth = maxDepth;
            this.structureTemplateManager = structureTemplateManager;
            this.pieces = pieces;
            this.random = random;
        }

        void tryPlacingChildren(
                PoolElementStructurePiece piece,
                VoxelShape free,
                int depth,
                boolean useExpansionHack,
                GenerationContext.HeightAccessor heightAccessor,
                PoolAliasLookup poolAliasLookup,
                LiquidSettings liquidSettings) {

            StructurePoolElement element = piece.getElement();
            BlockPos piecePos = piece.getPosition();
            Rotation rotation = piece.getRotation();
            Projection projection = element.getProjection();
            boolean rigid = projection == Projection.RIGID;
            VoxelShape localFree = null;
            BoundingBox pieceBox = piece.getBoundingBox();
            int pieceMinY = pieceBox.minY();

            outer:
            for (JigsawBlockInfo jigsaw : element.getShuffledJigsawBlocks(
                    this.structureTemplateManager, piecePos, rotation, this.random)) {
                StructureBlockInfo info = jigsaw.info();
                Direction front = JigsawBlock.getFrontFacing(info.state());
                BlockPos jigsawPos = info.pos();
                BlockPos connectPos = jigsawPos.offset(front);
                int k = jigsawPos.getY() - pieceMinY;
                int l = Integer.MIN_VALUE;

                ResourceKey<StructureTemplatePool> poolKey = poolAliasLookup.lookup(jigsaw.pool());
                Optional<StructureTemplatePool> poolOptional = this.pools.get(poolKey);
                if (poolOptional.isEmpty()) {
                    continue;
                }
                StructureTemplatePool pool = poolOptional.get();
                // Vanilla: skip only when the pool is empty AND is not the minecraft:empty sentinel.
                if (pool.size() == 0 && !this.pools.isEmptyPool(pool)) {
                    continue;
                }
                StructureTemplatePool fallbackPool = pool.getFallback().value();
                if (fallbackPool.size() == 0 && !this.pools.isEmptyPool(fallbackPool)) {
                    continue;
                }

                boolean connectInside = pieceBox.isInside(connectPos);
                VoxelShape freeShape;
                if (connectInside) {
                    if (localFree == null) {
                        localFree = VoxelShape.create(pieceBox);
                    }
                    freeShape = localFree;
                } else {
                    freeShape = free;
                }

                List<StructurePoolElement> candidates = new ArrayList<>();
                if (depth != this.maxDepth) {
                    candidates.addAll(pool.getShuffledTemplates(this.random));
                }
                candidates.addAll(fallbackPool.getShuffledTemplates(this.random));
                int priority = jigsaw.placementPriority();

                for (StructurePoolElement candidate : candidates) {
                    if (candidate == EmptyPoolElement.INSTANCE) {
                        break;
                    }
                    for (Rotation candidateRotation : Rotation.getShuffled(this.random)) {
                        List<JigsawBlockInfo> candidateJigsaws = candidate.getShuffledJigsawBlocks(
                                this.structureTemplateManager, BlockPos.ZERO, candidateRotation, this.random);
                        BoundingBox candidateBox = candidate.getBoundingBox(
                                this.structureTemplateManager, BlockPos.ZERO, candidateRotation);
                        int n = 0;
                        // The expansion-hack branch (bl && ySpan <= 16) never runs for trial chambers.
                        for (JigsawBlockInfo candidateJigsaw : candidateJigsaws) {
                            if (!JigsawBlock.canAttach(jigsaw, candidateJigsaw)) {
                                continue;
                            }
                            BlockPos candidateJigsawPos = candidateJigsaw.info().pos();
                            BlockPos placementPos = connectPos.subtract(candidateJigsawPos);
                            BoundingBox placementBox = candidate.getBoundingBox(
                                    this.structureTemplateManager, placementPos, candidateRotation);
                            int o = placementBox.minY();
                            Projection candidateProjection = candidate.getProjection();
                            boolean candidateRigid = candidateProjection == Projection.RIGID;
                            int p = candidateJigsawPos.getY();
                            int q = k - p + front.getStepY();
                            int r;
                            if (rigid && candidateRigid) {
                                r = pieceMinY + q;
                            } else {
                                if (l == Integer.MIN_VALUE) {
                                    throw new IllegalStateException(
                                            "Terrain height lookup (getFirstFreeHeight) is not supported; "
                                                    + "trial chambers use only RIGID projection");
                                }
                                r = l - p;
                            }
                            int s = r - o;
                            BoundingBox movedBox = placementBox.moved(0, s, 0);
                            BlockPos movedPos = placementPos.offset(0, s, 0);
                            if (n > 0) {
                                int t = Math.max(n + 1, movedBox.maxY() - movedBox.minY());
                                movedBox = movedBox.encapsulate(
                                        new BlockPos(movedBox.minX(), movedBox.minY() + t, movedBox.minZ()));
                            }

                            if (freeShape.joinIsNotEmpty(movedBox)) {
                                continue;
                            }
                            freeShape.subtract(movedBox);

                            int t = piece.getGroundLevelDelta();
                            int u;
                            if (candidateRigid) {
                                u = t - q;
                            } else {
                                u = candidate.getGroundLevelDelta();
                            }
                            PoolElementStructurePiece child = new PoolElementStructurePiece(
                                    this.structureTemplateManager, candidate, movedPos, u, candidateRotation, movedBox);
                            int v;
                            if (rigid) {
                                v = pieceMinY + k;
                            } else if (candidateRigid) {
                                v = r + p;
                            } else {
                                if (l == Integer.MIN_VALUE) {
                                    throw new IllegalStateException(
                                            "Terrain height lookup is not supported for non-rigid pieces");
                                }
                                v = l + q / 2;
                            }

                            piece.addJunction(new JigsawJunction(
                                    connectPos.getX(), v - k + t, connectPos.getZ(), q, candidateProjection));
                            child.addJunction(new JigsawJunction(
                                    jigsawPos.getX(), v - p + u, jigsawPos.getZ(), -q, projection));
                            this.pieces.add(child);
                            if (depth + 1 <= this.maxDepth) {
                                this.placing.add(new PieceState(child, freeShape, depth + 1), priority);
                            }
                            continue outer;
                        }
                    }
                }
            }
        }
    }

    /** Result of a jigsaw assembly: all placed pieces plus the overall bounding box. */
    public record JigsawResult(List<PoolElementStructurePiece> pieces, BoundingBox boundingBox) {
        public JigsawResult {
            pieces = List.copyOf(pieces);
        }
    }
}
