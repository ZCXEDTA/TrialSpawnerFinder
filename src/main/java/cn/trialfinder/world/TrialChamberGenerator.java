package cn.trialfinder.world;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SpawnerPoint;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a real trial-chamber structure start for a candidate coordinate and collects the
 * absolute positions of all trial-spawner blocks. Mojang-mapped port for 1.21.11 (the 1.21.1
 * {@code createStructureStart} API was renamed to {@code Structure.generate}).
 *
 * <p>This is the ground-truth generator: it uses the real Minecraft jigsaw worldgen, unlike the
 * self-contained {@code cn.trialfinder.sim.SimChamberGenerator}. The biome predicate accepts all
 * biomes so the acceptance criteria match the simulator (which has no biome filtering).
 */
public final class TrialChamberGenerator {
    private final ServerLevel world;
    private final Holder<Structure> trialChambers;

    public TrialChamberGenerator(ServerLevel world) {
        this.world = world;
        Registry<Structure> structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        this.trialChambers = structureRegistry.getOrThrow(
                ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("minecraft", "trial_chambers")));
    }

    public GeneratedChamber generate(BlockPoint candidate) {
        int chunkX = Math.floorDiv(candidate.x(), 16);
        int chunkZ = Math.floorDiv(candidate.z(), 16);

        StructureStart start = trialChambers.value().generate(
                trialChambers,
                world.dimension(),
                world.registryAccess(),
                world.getChunkSource().getGenerator(),
                world.getChunkSource().getGenerator().getBiomeSource(),
                world.getChunkSource().randomState(),
                world.getStructureManager(),
                world.getSeed(),
                new ChunkPos(chunkX, chunkZ),
                0,
                world,
                biome -> true);

        if (start == StructureStart.INVALID_START || !start.isValid()) {
            return new GeneratedChamber(candidate, List.of());
        }

        Set<SpawnerPoint> spawners = new HashSet<>();
        for (StructurePiece child : start.getPieces()) {
            if (child instanceof PoolElementStructurePiece piece) {
                collectSpawners(piece, spawners);
            }
        }
        List<SpawnerPoint> sorted = new ArrayList<>(spawners);
        sorted.sort(SpawnerPoint::compareTo);
        return new GeneratedChamber(candidate, sorted);
    }

    private void collectSpawners(PoolElementStructurePiece piece, Set<SpawnerPoint> output) {
        StructurePoolElement element = piece.getElement();
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
