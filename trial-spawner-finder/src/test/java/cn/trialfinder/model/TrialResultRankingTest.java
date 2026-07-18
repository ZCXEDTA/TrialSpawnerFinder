package cn.trialfinder.model;

import cn.minecraftfinder.core.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrialResultRankingTest {
    @Test
    void keepsTopOneHundredPerStructureCountThenSortsGlobally() {
        List<SearchResult> results = new ArrayList<>();
        for (int structures = 1; structures <= 3; structures++) {
            for (int score = 0; score < 105; score++) {
                results.add(new SearchResult(
                        score, structures, structures, score,
                        List.of(new BlockPoint(score, structures))));
            }
        }

        List<SearchResult> ranked = TrialResultRanking.rank(results);

        assertEquals(300, ranked.size());
        assertEquals(104, ranked.getFirst().spawnerCount());
        assertEquals(3, ranked.getFirst().structureCount());
        assertEquals(5, ranked.getLast().spawnerCount());
    }
}
