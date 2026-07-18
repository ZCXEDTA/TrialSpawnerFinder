package cn.minecraftfinder.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAreaTest {
    @TempDir
    Path directory;

    @Test
    void loadsSharedSearchProperties() throws Exception {
        Path path = directory.resolve("finder.properties");
        Files.writeString(path, """
                search-center-x=10
                search-center-z=-20
                search-radius-blocks=100
                search-area-shape=square
                full-world=false
                """);

        SearchArea area = SearchArea.load(FinderProperties.load(path), 1_000);

        assertTrue(area.contains(110, 80));
    }

    @Test
    void supportsCircleAndSquareSearchAreas() {
        SearchArea circle = new SearchArea(0, 0, 10, AreaShape.CIRCLE, false, 100);
        SearchArea square = new SearchArea(0, 0, 10, AreaShape.SQUARE, false, 100);

        assertFalse(circle.contains(10, 10));
        assertTrue(square.contains(10, 10));
    }

    @Test
    void clipsAtWorldBorder() {
        SearchArea area = new SearchArea(95, 95, 20, AreaShape.SQUARE, false, 100);

        assertTrue(area.contains(100, 100));
        assertFalse(area.contains(101, 100));
        assertTrue(area.bounds().contains(75, 75));
    }
}
