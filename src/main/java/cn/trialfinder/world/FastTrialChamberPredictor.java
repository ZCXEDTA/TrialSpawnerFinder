package cn.trialfinder.world;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 26.2 的快速布局路径。GenerationStub 使用与 Structure.generate 相同的
 * Jigsaw 实现，但不创建 StructureStart，因此不会复制一套易失效的布局算法。
 */
public final class FastTrialChamberPredictor {
    private final ServerLevel world;
    private final Structure trialChambers;
    private final Registry<Structure> structureRegistry;
    private final ChunkGenerator chunkGenerator;
    private final StructureTemplateManager templates;
    private final Map<TemplateKey, List<RelativeSpawner>> spawnerCache =
            new ConcurrentHashMap<>();

    public FastTrialChamberPredictor(ServerLevel world) {
        this.world = world;
        this.structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        this.trialChambers = structureRegistry.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "trial_chambers"));
        if (trialChambers == null) {
            throw new IllegalStateException("Minecraft 注册表中找不到 trial_chambers");
        }
        this.chunkGenerator = world.getChunkSource().getGenerator();
        this.templates = world.getStructureManager();
    }

    public Prediction predict(BlockPoint candidate) {
        int chunkX = Math.floorDiv(candidate.x(), 16);
        int chunkZ = Math.floorDiv(candidate.z(), 16);
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        Structure.GenerationContext context = new Structure.GenerationContext(
                world.registryAccess(), chunkGenerator, chunkGenerator.getBiomeSource(),
                world.getChunkSource().randomState(), templates, world.getSeed(), chunk,
                world, trialChambers.biomes()::contains);
        return trialChambers.findValidGenerationPoint(context)
                .map(Structure.GenerationStub::getPiecesBuilder)
                .map(builder -> collect(candidate, builder.build()))
                .orElseGet(() -> new Prediction(candidate, false, List.of()));
    }

    private Prediction collect(BlockPoint candidate, PiecesContainer pieces) {
        Set<SpawnerPoint> spawners = new HashSet<>();
        for (var piece : pieces.pieces()) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                collectSpawners(poolPiece.getElement(), poolPiece, spawners);
            }
        }
        List<SpawnerPoint> sorted = new ArrayList<>(spawners);
        sorted.sort(SpawnerPoint::compareTo);
        return new Prediction(candidate, !sorted.isEmpty(), sorted);
    }

    private void collectSpawners(
            StructurePoolElement element, PoolElementStructurePiece piece,
            Set<SpawnerPoint> output) {
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : list.getElements()) {
                collectSpawners(child, piece, output);
            }
            return;
        }
        if (!(element instanceof SinglePoolElement single)) return;
        TemplateKey key = new TemplateKey(single.getTemplateLocation(), piece.getRotation());
        for (RelativeSpawner relative : spawnerCache.computeIfAbsent(key, this::loadSpawners)) {
            output.add(new SpawnerPoint(
                    piece.getPosition().getX() + relative.x(),
                    piece.getPosition().getY() + relative.y(),
                    piece.getPosition().getZ() + relative.z()));
        }
    }

    private List<RelativeSpawner> loadSpawners(TemplateKey key) {
        StructureTemplate template = templates.getOrCreate(key.template());
        StructurePlaceSettings placement = new StructurePlaceSettings().setRotation(key.rotation());
        List<RelativeSpawner> result = template.filterBlocks(BlockPos.ZERO, placement, Blocks.TRIAL_SPAWNER)
                .stream()
                .map(info -> new RelativeSpawner(info.pos().getX(), info.pos().getY(), info.pos().getZ()))
                .toList();
        return result;
    }

    public record Prediction(BlockPoint position, boolean exists, List<SpawnerPoint> spawners) {
        public Prediction {
            spawners = List.copyOf(spawners);
        }

        public List<SpawnerPoint> actualSpawners() {
            return spawners;
        }

        public List<SpawnerPoint> theoreticalSpawners() {
            return spawners;
        }
    }

    private record TemplateKey(Identifier template, Rotation rotation) {
    }

    private record RelativeSpawner(int x, int y, int z) {
    }
}
