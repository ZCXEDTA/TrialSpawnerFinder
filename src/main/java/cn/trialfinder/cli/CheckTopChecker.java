package cn.trialfinder.cli;

import cn.trialfinder.model.BlockPoint;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.sim.SimChamberGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspects the top-N search results and tallies, for each result's member chambers, the number of
 * fast and slow trial spawners and the number of vaults.
 *
 * <p>A chamber is generated via {@link SimChamberGenerator#generateChamber} from each member
 * structure's chunk. A spawner is <b>slow</b> when its config is under the {@code slow_ranged}
 * category (ticks_between_spawn = 160); all others are <b>fast</b> (ticks_between_spawn = 20).
 */
public final class CheckTopChecker {

    /** Per-result tally: fast spawners, slow spawners, vaults. */
    public record CheckResult(int fastSpawners, int slowSpawners, int vaults) {
        public int totalSpawners() {
            return this.fastSpawners + this.slowSpawners;
        }
    }

    private CheckTopChecker() {
    }

    /**
     * Inspects the top {@code checkTop} results (or all when {@code checkTop <= 0}).
     *
     * @param seed     world seed used to generate chambers
     * @param results  the search results, in output order
     * @param generator the chamber generator (may be null to skip, returning empty tallies)
     * @param checkTop number of leading results to inspect; {@code <= 0} inspects none
     */
    public static List<CheckResult> check(long seed, List<SearchResult> results,
                                          SimChamberGenerator generator, int checkTop) {
        if (checkTop <= 0 || generator == null || results == null) {
            return List.of();
        }
        int limit = Math.min(checkTop, results.size());
        List<CheckResult> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(inspect(seed, results.get(i), generator));
        }
        return out;
    }

    private static CheckResult inspect(long seed, SearchResult result, SimChamberGenerator generator) {
        int fast = 0;
        int slow = 0;
        int vaults = 0;
        for (BlockPoint structure : result.structures()) {
            int chunkX = Math.floorDiv(structure.x(), 16);
            int chunkZ = Math.floorDiv(structure.z(), 16);
            SimChamberGenerator.ChamberResult chamber =
                    generator.generateChamber(seed, chunkX, chunkZ).orElse(null);
            if (chamber == null) {
                continue;
            }
            for (SimChamberGenerator.SpawnerInfo info : chamber.spawnerInfos()) {
                if (isSlowSpawner(info.config())) {
                    slow++;
                } else {
                    fast++;
                }
            }
            vaults += chamber.vaultInfos().size();
        }
        return new CheckResult(fast, slow, vaults);
    }

    /** True when the config id is under the {@code slow_ranged} category (ticks_between_spawn = 160). */
    private static boolean isSlowSpawner(String config) {
        return config != null && config.contains("slow_ranged");
    }
}
