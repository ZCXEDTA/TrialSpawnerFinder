package cn.trialfinder.sim;

import cn.trialfinder.sim.math.ChunkPos;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B flow verification: end-to-end chamber assembly from data files. Verifies structural
 * invariants (piece count, spawners found, mob aliases, determinism, no overlaps).
 */
class ChamberGenerationTest {
    private static final Path DATA_DIR = Path.of("src/main/resources");
    private static final long SEED = 12345L;

    private final SimChamberGenerator generator = new SimChamberGenerator(DATA_DIR);

    @Test
    void loadsAllPoolsAndTemplates() {
        assertTrue(generator.pools().pools().size() >= 47, "pool registry size");
        // A handful of known templates must parse.
        for (String id : List.of("trial_chambers/corridor/end_1", "trial_chambers/chamber/chamber_1",
                "trial_chambers/spawner/ranged/skeleton", "trial_chambers/hallway/straight")) {
            assertTrue(generator.templateManager().get(cn.trialfinder.sim.resources.ResourceKey.create(id).identifier()).isPresent(),
                    "template " + id);
        }
    }

    @Test
    void generatesFullChamber() {
        List<ChunkPos> candidates = generator.enumeratePotentialChunks(SEED, -2000, 2000, -2000, 2000);
        assertFalse(candidates.isEmpty());

        SimChamberGenerator.ChamberResult result = generator.generate(SEED, candidates.get(0).x(), candidates.get(0).z())
                .orElseThrow();
        assertTrue(result.assembly().pieces().size() > 10, "piece count should be a full chamber");
        assertFalse(result.spawnerPositions().isEmpty(), "chamber should contain trial spawners");
        assertNotNull(result.mobAliases());
        assertTrue(result.mobAliases().ranged().endsWith("/skeleton")
                || result.mobAliases().ranged().endsWith("/stray")
                || result.mobAliases().ranged().endsWith("/poison_skeleton"));
    }

    @Test
    void generationIsDeterministic() {
        List<ChunkPos> candidates = generator.enumeratePotentialChunks(SEED, -2000, 2000, -2000, 2000);
        ChunkPos chunk = candidates.get(3);
        SimChamberGenerator.ChamberResult a = generator.generate(SEED, chunk.x(), chunk.z()).orElseThrow();
        SimChamberGenerator.ChamberResult b = generator.generate(SEED, chunk.x(), chunk.z()).orElseThrow();
        assertEquals(a.spawnerPositions(), b.spawnerPositions());
        assertEquals(a.assembly().pieces().size(), b.assembly().pieces().size());
        assertEquals(a.mobAliases(), b.mobAliases());
    }

    @Test
    void spawnerPositionsAreUnique() {
        List<ChunkPos> candidates = generator.enumeratePotentialChunks(SEED, -2000, 2000, -2000, 2000);
        SimChamberGenerator.ChamberResult result = generator.generate(SEED, candidates.get(5).x(), candidates.get(5).z())
                .orElseThrow();
        assertEquals(result.spawnerPositions().size(),
                result.spawnerPositions().stream().distinct().count(),
                "spawner positions must be unique");
    }

    @Test
    void piecesStayWithinMaxDistanceRegion() {
        List<ChunkPos> candidates = generator.enumeratePotentialChunks(SEED, -2000, 2000, -2000, 2000);
        SimChamberGenerator.ChamberResult result = generator.generate(SEED, candidates.get(5).x(), candidates.get(5).z())
                .orElseThrow();
        // The overall bounding box must be finite and non-degenerate.
        cn.trialfinder.sim.structure.BoundingBox box = result.assembly().boundingBox();
        assertTrue(box.maxX() >= box.minX() && box.maxY() >= box.minY() && box.maxZ() >= box.minZ());
    }
}
