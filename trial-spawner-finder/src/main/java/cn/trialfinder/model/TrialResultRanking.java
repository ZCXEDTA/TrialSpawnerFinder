package cn.trialfinder.model;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class TrialResultRanking {
    private TrialResultRanking() {
    }

    public static List<SearchResult> rank(Collection<SearchResult> results) {
        return results.stream()
                .collect(Collectors.groupingBy(SearchResult::structureCount))
                .values().stream()
                .flatMap(group -> group.stream().sorted().limit(100))
                .sorted()
                .toList();
    }
}
