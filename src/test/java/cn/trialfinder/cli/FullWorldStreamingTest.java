package cn.trialfinder.cli;

import cn.trialfinder.accel.CpuAccelerator;
import cn.trialfinder.sim.SimChamberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the streaming/tiling correctness of {@link SearchEngine#searchRegion}: splitting a
 * rectangular region into 4 sub-regions (with overlap) and merging their results must produce
 * exactly the same results as searching the whole region once. This is the core invariant that
 * makes {@code --full-world} (many tiles) equivalent to a single all-at-once search.
 */
class FullWorldStreamingTest {

    private static final long SEED = 188188L;

    @Test
    void fourTileSplitMatchesSingleRegion() throws IOException {
        CpuAccelerator acc = new CpuAccelerator();
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        SearchEngine.Options opts = new SearchEngine.Options(
                SEED, 10_000, 128, 1, 1, false, 4, false, 100_000, 1_000);

        // Whole region.
        SearchRegion whole = new SearchRegion(-5000, 5000, -5000, 5000, 0);

        List<ResultEntry> baseline = SearchEngine.searchRegion(generator, whole, opts, acc,
                new SearchEngine.RegionStats());

        // Split into four quadrants (partition at x=0 / z=0, cores are disjoint and gap-free).
        List<SearchRegion> quadrants = List.of(
                new SearchRegion(-5000, -1, -5000, -1, 0),
                new SearchRegion(0, 5000, -5000, -1, 1),
                new SearchRegion(-5000, -1, 0, 5000, 2),
                new SearchRegion(0, 5000, 0, 5000, 3));

        List<ResultEntry> merged = new ArrayList<>();
        long chambers = 0;
        for (SearchRegion region : quadrants) {
            SearchEngine.RegionStats stats = new SearchEngine.RegionStats();
            merged.addAll(SearchEngine.searchRegion(generator, region, opts, acc, stats));
            chambers += stats.chamberCount;
        }
        merged.sort(ResultEntry::compareTo);

        assertEquals(baseline, merged,
                "tiled search with overlap must equal a single-region search");
        System.out.println("fourTileSplitMatchesSingleRegion: baseline=" + baseline.size()
                + " merged=" + merged.size() + " chambers=" + chambers);
    }

    @Test
    void overlappingWorldTilesWithOwnsProduceNoDuplicates() throws IOException {
        CpuAccelerator acc = new CpuAccelerator();
        SimChamberGenerator generator = SimChamberGenerator.fromClasspath();
        SearchEngine.Options opts = new SearchEngine.Options(
                SEED, 10_000, 128, 1, 1, false, 4, false, 4_000, 500);

        // Small world (±10,000), tileSize=4,000, overlap=500, step=3,500. Core tiles overlap by
        // 500 blocks, but owning regions partition the world. Take tile columns tx/tz in {1,2,3};
        // their owning-union is exactly [owningStart(1), owningEnd(3)]^2.
        WorldTiler tiler = new WorldTiler(4_000, 500, 10_000);
        SearchRegion union = new SearchRegion(
                tiler.owningStart(1), tiler.owningEnd(3),
                tiler.owningStart(1), tiler.owningEnd(3), 0);

        // Baseline: single-region search of the owning-union (region.contains ownership).
        List<ResultEntry> baseline = SearchEngine.searchRegion(generator, union, opts, acc,
                new SearchEngine.RegionStats());

        // Tiled: the 9 tiles whose columns/tows are in {1,2,3}, using tiler.owns() ownership.
        List<SearchRegion> covering = new ArrayList<>();
        for (int tx = 1; tx <= 3; tx++) {
            for (int tz = 1; tz <= 3; tz++) {
                covering.add(tiler.getTiles().get(tx * tiler.tilesPerDim() + tz));
            }
        }
        assertEquals(9, covering.size(), "3x3 tiles should exactly cover the owning-union");

        List<ResultEntry> merged = new ArrayList<>();
        long chambers = 0;
        for (SearchRegion tile : covering) {
            SearchEngine.RegionStats stats = new SearchEngine.RegionStats();
            merged.addAll(SearchEngine.searchRegion(generator, tile, tiler, opts, acc, stats));
            chambers += stats.chamberCount;
        }
        merged.sort(ResultEntry::compareTo);

        // Overlapping-tiler ownership must yield exactly the same results (no duplicates, no
        // missing clusters) as the single-region search.
        assertEquals(baseline, merged,
                "overlapping WorldTiler tiles + owns() must equal a single-region search");
        System.out.println("overlappingWorldTilesWithOwns: tiles=" + covering.size()
                + " baseline=" + baseline.size() + " merged=" + merged.size());
    }
}
