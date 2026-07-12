package cn.trialfinder.model;

import cn.minecraftfinder.core.BlockPoint;

import java.util.List;

public record SearchResult(
        long centerX,
        long centerZ,
        int structureCount,
        int spawnerCount,
        List<BlockPoint> structures) implements Comparable<SearchResult> {

    public SearchResult {
        structures = List.copyOf(structures);
    }

    @Override
    public int compareTo(SearchResult other) {
        int bySpawners = Integer.compare(other.spawnerCount, spawnerCount);
        if (bySpawners != 0) return bySpawners;
        int byStructures = Integer.compare(other.structureCount, structureCount);
        if (byStructures != 0) return byStructures;
        int byX = Long.compare(centerX, other.centerX);
        return byX != 0 ? byX : Long.compare(centerZ, other.centerZ);
    }
}
