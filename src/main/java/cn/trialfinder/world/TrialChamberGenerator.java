package cn.trialfinder.world;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TrialChamberGenerator {
    private final ServerLevel world;
    private final Structure trialChambers;
    private final Registry<Structure> structureRegistry;
    private final ChunkGenerator chunkGenerator;

    public TrialChamberGenerator(ServerLevel world) {
        this.world = world;
        this.structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        this.trialChambers = structureRegistry.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "trial_chambers"));
        if (trialChambers == null) {
            throw new IllegalStateException("Minecraft 注册表中找不到 trial_chambers");
        }
        this.chunkGenerator = world.getChunkSource().getGenerator();
    }

    public GeneratedChamber generate(BlockPoint candidate) {
        int chunkX = Math.floorDiv(candidate.x(), 16);
        int chunkZ = Math.floorDiv(candidate.z(), 16);
        StructureStart start = trialChambers.generate(
                structureRegistry.wrapAsHolder(trialChambers),
                world.dimension(),
                world.registryAccess(),
                chunkGenerator,
                chunkGenerator.getBiomeSource(),
                world.getChunkSource().randomState(),
                world.getStructureManager(),
                world.getSeed(),
                new ChunkPos(chunkX, chunkZ),
                0,
                world,
                trialChambers.biomes()::contains);
        if (!start.isValid()) {
            return new GeneratedChamber(candidate, List.of());
        }

        Set<SpawnerPoint> spawners = new HashSet<>();
        for (StructurePiece child : start.getPieces()) {
            if (!(child instanceof PoolElementStructurePiece piece)) {
                continue;
            }
            collectSpawners(piece.getElement(), piece, spawners);
        }
        List<SpawnerPoint> sorted = new ArrayList<>(spawners);
        sorted.sort(SpawnerPoint::compareTo);
        return new GeneratedChamber(candidate, sorted);
    }

    private void collectSpawners(StructurePoolElement element, PoolElementStructurePiece piece,
                                 Set<SpawnerPoint> output) {
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : list.getElements()) {
                collectSpawners(child, piece, output);
            }
            return;
        }
        if (!(element instanceof SinglePoolElement single)) {
            return;
        }

        StructureTemplate template = world.getStructureManager().getOrCreate(single.getTemplateLocation());
        StructurePlaceSettings placement = new StructurePlaceSettings().setRotation(piece.getRotation());
        for (StructureTemplate.StructureBlockInfo block :
                template.filterBlocks(piece.getPosition(), placement, Blocks.TRIAL_SPAWNER)) {
            output.add(new SpawnerPoint(block.pos().getX(), block.pos().getY(), block.pos().getZ()));
        }
    }

    public record GeneratedChamber(BlockPoint position, List<SpawnerPoint> spawners) {
        public GeneratedChamber {
            spawners = List.copyOf(spawners);
        }

        public boolean exists() {
            return !spawners.isEmpty();
        }
    }
}
