package cn.trialfinder.model;

import cn.minecraftfinder.core.BlockPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class TrialResultAccumulator {
    public static final int RESULTS_PER_STRUCTURE_COUNT = 100;

    private final Map<Integer, ResultGroup> groups = new HashMap<>();

    public synchronized void accept(SearchResult result) {
        groups.computeIfAbsent(result.structureCount(), ignored -> new ResultGroup())
                .accept(result);
    }

    public synchronized List<SearchResult> results() {
        return groups.values().stream()
                .flatMap(group -> group.results.stream())
                .sorted()
                .toList();
    }

    public synchronized OptionalInt cutoffSpawnerCount(int structureCount) {
        ResultGroup group = groups.get(structureCount);
        if (group == null || group.results.size() < RESULTS_PER_STRUCTURE_COUNT) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(group.results.getLast().spawnerCount());
    }

    public synchronized boolean canDiscardUpperBound(
            int upperBound, int minimumStructures, int maximumStructures) {
        for (int structures = minimumStructures; structures <= maximumStructures; structures++) {
            OptionalInt cutoff = cutoffSpawnerCount(structures);
            if (cutoff.isEmpty() || upperBound >= cutoff.getAsInt()) return false;
        }
        return true;
    }

    public synchronized void clear() {
        groups.clear();
    }

    private static final class ResultGroup {
        private final List<SearchResult> results = new ArrayList<>(RESULTS_PER_STRUCTURE_COUNT + 1);
        private final Map<List<BlockPoint>, SearchResult> byStructures = new HashMap<>();

        private void accept(SearchResult candidate) {
            SearchResult existing = byStructures.get(candidate.structures());
            if (existing != null) {
                if (candidate.compareTo(existing) >= 0) return;
                results.remove(existing);
            } else if (results.size() == RESULTS_PER_STRUCTURE_COUNT
                    && candidate.compareTo(results.getLast()) >= 0) {
                return;
            }

            int low = 0;
            int high = results.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (results.get(middle).compareTo(candidate) <= 0) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            results.add(low, candidate);
            byStructures.put(candidate.structures(), candidate);
            if (results.size() > RESULTS_PER_STRUCTURE_COUNT) {
                SearchResult removed = results.removeLast();
                byStructures.remove(removed.structures(), removed);
            }
        }
    }
}
