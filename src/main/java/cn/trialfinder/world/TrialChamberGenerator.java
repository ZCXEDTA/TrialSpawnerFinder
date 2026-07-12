package cn.trialfinder.world;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;
import cn.trialfinder.mixin.ListPoolElementAccessor;
import cn.trialfinder.mixin.SinglePoolElementAccessor;
import com.mojang.datafixers.util.Either;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.ListPoolElement;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TrialChamberGenerator {
    private final ServerWorld world;
    private final Structure trialChambers;
    private final ChunkGenerator chunkGenerator;

    public TrialChamberGenerator(ServerWorld world) {
        this.world = world;
        this.trialChambers = world.getRegistryManager()
                .get(RegistryKeys.STRUCTURE)
                .get(Identifier.of("minecraft", "trial_chambers"));
        if (trialChambers == null) {
            throw new IllegalStateException("Minecraft 注册表中找不到 trial_chambers");
        }
        this.chunkGenerator = world.getChunkManager().getChunkGenerator();
    }

    public GeneratedChamber generate(BlockPoint candidate) {
        int chunkX = Math.floorDiv(candidate.x(), 16);
        int chunkZ = Math.floorDiv(candidate.z(), 16);
        StructureStart start = trialChambers.createStructureStart(
                world.getRegistryManager(),
                chunkGenerator,
                chunkGenerator.getBiomeSource(),
                world.getChunkManager().getNoiseConfig(),
                world.getStructureTemplateManager(),
                world.getSeed(),
                new ChunkPos(chunkX, chunkZ),
                0,
                world,
                trialChambers.getValidBiomes()::contains);
        if (!start.hasChildren()) {
            return new GeneratedChamber(candidate, List.of());
        }

        Set<SpawnerPoint> spawners = new HashSet<>();
        for (StructurePiece child : start.getChildren()) {
            if (!(child instanceof PoolStructurePiece piece)) {
                continue;
            }
            collectSpawners(piece.getPoolElement(), piece, spawners);
        }
        List<SpawnerPoint> sorted = new ArrayList<>(spawners);
        sorted.sort(SpawnerPoint::compareTo);
        return new GeneratedChamber(candidate, sorted);
    }

    private void collectSpawners(StructurePoolElement element, PoolStructurePiece piece,
                                 Set<SpawnerPoint> output) {
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : ((ListPoolElementAccessor) list).trialFinder$getElements()) {
                collectSpawners(child, piece, output);
            }
            return;
        }
        if (!(element instanceof SinglePoolElement single)) {
            return;
        }

        Either<Identifier, StructureTemplate> location =
                ((SinglePoolElementAccessor) single).trialFinder$getLocation();
        StructureTemplate template = location.map(
                world.getStructureTemplateManager()::getTemplateOrBlank,
                embedded -> embedded);
        StructurePlacementData placement = new StructurePlacementData().setRotation(piece.getRotation());
        for (StructureTemplate.StructureBlockInfo block :
                template.getInfosForBlock(piece.getPos(), placement, Blocks.TRIAL_SPAWNER)) {
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
