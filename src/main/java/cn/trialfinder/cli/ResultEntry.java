package cn.trialfinder.cli;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV-serializable form of a search result, used as the unit written to per-tile temp files and
 * merged by {@link ResultMerger}. Sorts exactly like {@link SearchResult}: spawner count
 * descending, then structure count descending, then X, then Z.
 */
public record ResultEntry(
        long centerX,
        long centerZ,
        int structureCount,
        int spawnerCount,
        List<BlockPoint> structures) implements Comparable<ResultEntry> {

    public ResultEntry {
        structures = List.copyOf(structures);
    }

    @Override
    public int compareTo(ResultEntry other) {
        int bySpawners = Integer.compare(other.spawnerCount, spawnerCount);
        if (bySpawners != 0) return bySpawners;
        int byStructures = Integer.compare(other.structureCount, structureCount);
        if (byStructures != 0) return byStructures;
        int byX = Long.compare(centerX, other.centerX);
        return byX != 0 ? byX : Long.compare(centerZ, other.centerZ);
    }

    public SearchResult toSearchResult() {
        return new SearchResult(this.centerX, this.centerZ, this.structureCount,
                this.spawnerCount, this.structures);
    }

    public static ResultEntry from(SearchResult r) {
        return new ResultEntry(r.centerX(), r.centerZ(), r.structureCount(),
                r.spawnerCount(), r.structures());
    }

    /** Serializes to a single CSV line (no trailing newline). */
    public String toCsvLine() {
        String positions = this.structures.stream()
                .map(p -> p.x() + "," + p.z())
                .collect(Collectors.joining("|"));
        return this.centerX + ";" + this.centerZ + ";" + this.structureCount
                + ";" + this.spawnerCount + ";" + positions;
    }

    /** Parses a line produced by {@link #toCsvLine()}. */
    public static ResultEntry parse(String line) {
        int sep1 = line.indexOf(';');
        int sep2 = line.indexOf(';', sep1 + 1);
        int sep3 = line.indexOf(';', sep2 + 1);
        int sep4 = line.indexOf(';', sep3 + 1);
        long centerX = Long.parseLong(line.substring(0, sep1));
        long centerZ = Long.parseLong(line.substring(sep1 + 1, sep2));
        int structureCount = Integer.parseInt(line.substring(sep2 + 1, sep3));
        int spawnerCount = Integer.parseInt(line.substring(sep3 + 1, sep4));
        String positions = line.substring(sep4 + 1);
        List<BlockPoint> structures = new ArrayList<>();
        if (!positions.isEmpty()) {
            for (String pair : positions.split("\\|")) {
                int comma = pair.indexOf(',');
                structures.add(new BlockPoint(
                        Integer.parseInt(pair.substring(0, comma)),
                        Integer.parseInt(pair.substring(comma + 1))));
            }
        }
        return new ResultEntry(centerX, centerZ, structureCount, spawnerCount, structures);
    }
}
