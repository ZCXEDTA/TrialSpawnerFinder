package cn.minecraftfinder.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchBoundsTest {
    @Test
    void clipsSearchAreaAtWorldBorder() {
        SearchBounds bounds = SearchBounds.around(-29_999_900, 29_999_900, 1_000, 30_000_000);

        assertEquals(-30_000_000, bounds.minX());
        assertEquals(-29_998_900, bounds.maxX());
        assertEquals(29_998_900, bounds.minZ());
        assertEquals(30_000_000, bounds.maxZ());
        assertTrue(bounds.contains(-30_000_000, 30_000_000));
        assertFalse(bounds.contains(-30_000_001, 30_000_000));
    }

    @Test
    void createsFullWorldBounds() {
        SearchBounds bounds = SearchBounds.fullWorld(30_000_000);

        assertEquals(new SearchBounds(
                -30_000_000, 30_000_000, -30_000_000, 30_000_000), bounds);
    }
}
