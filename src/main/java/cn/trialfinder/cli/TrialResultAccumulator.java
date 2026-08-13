package cn.trialfinder.cli;

import cn.trialfinder.model.SearchResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Accumulates search results grouped by structure count, keeping the top {@link #RESULTS_PER_STRUCTURE_COUNT}
 * per group (the final output is truncated to 100 per structure-count group, matching the CLI).
 * Used by the cluster-level prune-and-generate pipeline to decide, for a candidate cluster with an
 * upper-bound spawner count, whether it can possibly make the output — if its upper bound is below
 * the current cutoff of every structure-count group it could produce, it is dropped without running
 * the expensive full B-flow generation.
 *
 * <p>Port of the 26.2 branch's {@code TrialResultAccumulator} (commit d6a3b11).
 */
public final class TrialResultAccumulator {
    /** Final output keeps at most this many results per structure-count group. */
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

    /** The spawner count of the lowest result currently kept for {@code structureCount}, if the
     * group is full (i.e. would drop any result below this cutoff). */
    public synchronized OptionalInt cutoffSpawnerCount(int structureCount) {
        ResultGroup group = groups.get(structureCount);
        if (group == null || group.results.size() < RESULTS_PER_STRUCTURE_COUNT) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(group.results.getLast().spawnerCount());
    }

    /**
     * True when a cluster whose structures span {@code [minimumStructures, maximumStructures]} and
     * whose predicted spawner upper bound is {@code upperBound} cannot make the output: for every
     * structure count in that range, the group is full and its cutoff is strictly above
     * {@code upperBound}. Dropping such a cluster never changes the final result set (lossless).
     */
    public synchronized boolean canDiscardUpperBound(
            int upperBound, int minimumStructures, int maximumStructures) {
        for (int structures = minimumStructures; structures <= maximumStructures; structures++) {
            OptionalInt cutoff = cutoffSpawnerCount(structures);
            if (cutoff.isEmpty() || upperBound >= cutoff.getAsInt()) {
                return false;
            }
        }
        return true;
    }

    public synchronized void clear() {
        groups.clear();
    }

    private static final class ResultGroup {
        private final List<SearchResult> results = new ArrayList<>(RESULTS_PER_STRUCTURE_COUNT + 1);
        private final Map<List<cn.trialfinder.model.BlockPoint>, SearchResult> byStructures = new HashMap<>();

        private void accept(SearchResult candidate) {
            SearchResult existing = byStructures.get(candidate.structures());
            if (existing != null) {
                if (candidate.compareTo(existing) >= 0) {
                    return;
                }
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
