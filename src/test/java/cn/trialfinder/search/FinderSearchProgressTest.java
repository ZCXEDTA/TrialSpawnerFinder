package cn.trialfinder.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinderSearchProgressTest {
    @Test
    void fineProgressShowsThroughputAndEta() {
        assertEquals("[生成 ##--------] 25% 50/200 | 5.0 座/秒 | ETA 00:00:30",
                FinderSearch.phaseProgressLine("生成", 50, 200, "座", 10_000_000_000L));
    }
}
