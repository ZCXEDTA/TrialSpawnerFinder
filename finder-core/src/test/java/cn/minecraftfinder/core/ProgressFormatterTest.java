package cn.minecraftfinder.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressFormatterTest {
    @Test
    void formatsPhaseProgress() {
        assertEquals("[生成 ##--------] 25% 50/200 | 5.0 座/秒 | ETA 00:00:30",
                ProgressFormatter.phase("生成", 50, 200, "座", 10_000_000_000L));
    }

    @Test
    void formatsEstimatedWorkProgress() {
        assertEquals("[粗筛 ##--------] 25% 500000/2000000 | 500.0k 个 | 50.0k 个/秒 | ETA 00:00:30",
                ProgressFormatter.estimatedWork(
                        "粗筛", 25, 100, 500_000, 2_000_000, "个", 10_000_000_000L));
    }

    @Test
    void excludesRestoredWorkFromThroughput() {
        assertEquals("[总进度 ##--------] 25% 500000/2000000 | 500.0k 个 | 10.0k 个/秒 | ETA 00:02:30",
                ProgressFormatter.estimatedWork(
                        "总进度", 25, 100, 500_000, 2_000_000,
                        "个", 400_000, 10_000_000_000L));
    }
}
