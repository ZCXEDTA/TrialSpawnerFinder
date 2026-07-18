package cn.trialfinder.model;

import cn.minecraftfinder.core.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialResultAccumulatorTest {
    @Test
    void matchesExistingUniqueAndRankingSemanticsInAnyOrder() {
        Random random = new Random(42);
        List<SearchResult> input = new ArrayList<>();
        for (int index = 0; index < 2_000; index++) {
            int key = random.nextInt(700);
            int structures = 1 + key % 4;
            input.add(new SearchResult(
                    random.nextInt(20_000), random.nextInt(20_000), structures,
                    random.nextInt(80), List.of(new BlockPoint(key, structures))));
        }

        Map<List<BlockPoint>, SearchResult> unique = new LinkedHashMap<>();
        input.forEach(result -> unique.merge(
                result.structures(), result,
                (first, second) -> first.compareTo(second) <= 0 ? first : second));

        TrialResultAccumulator accumulator = new TrialResultAccumulator();
        input.forEach(accumulator::accept);

        assertEquals(TrialResultRanking.rank(unique.values()), accumulator.results());
    }

    @Test
    void exposesCutoffOnlyAfterGroupIsFull() {
        TrialResultAccumulator accumulator = new TrialResultAccumulator();
        for (int score = 0; score < 99; score++) {
            accumulator.accept(result(score));
        }
        assertFalse(accumulator.cutoffSpawnerCount(1).isPresent());

        accumulator.accept(result(99));
        assertTrue(accumulator.cutoffSpawnerCount(1).isPresent());
        assertEquals(0, accumulator.cutoffSpawnerCount(1).orElseThrow());

        accumulator.accept(result(100));
        assertEquals(1, accumulator.cutoffSpawnerCount(1).orElseThrow());
        assertFalse(accumulator.canDiscardUpperBound(1, 1, 1));
        assertTrue(accumulator.canDiscardUpperBound(0, 1, 1));
    }

    @Test
    void keepsBoundedStateAcrossTenMillionCandidates() {
        TrialResultAccumulator accumulator = new TrialResultAccumulator();
        for (int index = 0; index < 10_615_784; index++) {
            accumulator.accept(new SearchResult(
                    index, 0, 1, index % 1_000,
                    List.of(new BlockPoint(index, 0))));
        }

        assertEquals(100, accumulator.results().size());
        assertEquals(999, accumulator.results().getFirst().spawnerCount());
    }

    private static SearchResult result(int score) {
        return new SearchResult(score, 0, 1, score, List.of(new BlockPoint(score, 0)));
    }
}
