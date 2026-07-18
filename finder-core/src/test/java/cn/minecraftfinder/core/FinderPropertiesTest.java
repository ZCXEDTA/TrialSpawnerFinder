package cn.minecraftfinder.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinderPropertiesTest {
    @TempDir
    Path directory;

    @Test
    void readsRequiredAndOptionalValues() throws Exception {
        Path path = directory.resolve("finder.properties");
        Files.writeString(path, "seed=-1\nthreads=4\nenabled=true\n");

        FinderProperties properties = FinderProperties.load(path);

        assertEquals(-1, properties.requiredLong("seed"));
        assertEquals(4, properties.optionalInt("threads", 1));
        assertEquals(true, properties.optionalBoolean("enabled", false));
        assertEquals("fallback", properties.optional("missing", "fallback"));
    }

    @Test
    void rejectsMissingAndMalformedValues() throws Exception {
        Path path = directory.resolve("finder.properties");
        Files.writeString(path, "number=nope\nflag=maybe\n");
        FinderProperties properties = FinderProperties.load(path);

        assertThrows(IllegalArgumentException.class, () -> properties.required("missing"));
        assertThrows(IllegalArgumentException.class, () -> properties.requiredLong("number"));
        assertThrows(IllegalArgumentException.class, () -> properties.optionalBoolean("flag", false));
    }
}
