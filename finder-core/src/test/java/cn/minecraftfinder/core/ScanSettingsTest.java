package cn.minecraftfinder.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanSettingsTest {
    @TempDir
    Path directory;

    @Test
    void loadsSharedScanSettings() throws Exception {
        Path path = directory.resolve("finder.properties");
        Files.writeString(path, "scan-threads=4\nscan-shard-size-blocks=8192\n");

        ScanSettings settings = ScanSettings.load(FinderProperties.load(path));

        assertEquals(4, settings.threads());
        assertEquals(8192, settings.shardSizeBlocks());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new ScanSettings(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ScanSettings(1, 0));
    }
}
