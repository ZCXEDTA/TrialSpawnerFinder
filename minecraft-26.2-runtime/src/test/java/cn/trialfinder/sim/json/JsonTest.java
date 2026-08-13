package cn.trialfinder.sim.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesObject() {
        Json.Object root = (Json.Object) Json.parse(
                "{\"elements\":[{\"weight\":1}],\"fallback\":\"minecraft:empty\"}");
        assertEquals(1, root.getArray("elements").size());
        assertEquals(1, ((Json.Object) root.getArray("elements").get(0)).getInt("weight"));
        assertEquals("minecraft:empty", root.getString("fallback"));
    }

    @Test
    void parsesAliasJson() {
        Json.Object root = (Json.Object) Json.parse("""
                {
                  "type": "minecraft:random",
                  "alias": "minecraft:trial_chambers/spawner/contents/melee",
                  "targets": [
                    { "data": "minecraft:trial_chambers/spawner/melee/zombie", "weight": 1 },
                    { "data": "minecraft:trial_chambers/spawner/melee/husk", "weight": 1 }
                  ]
                }
                """);
        assertEquals("minecraft:random", root.getString("type"));
        Json.Array targets = root.getArray("targets");
        assertEquals(2, targets.size());
        assertEquals("minecraft:trial_chambers/spawner/melee/zombie",
                ((Json.Object) targets.get(0)).getString("data"));
    }

    @Test
    void parsesStringEscapes() {
        Json.Str s = (Json.Str) Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u0041\"");
        assertEquals("a\"b\\c/d\n\tA", s.value());
    }

    @Test
    void parsesScalars() {
        assertEquals(42, ((Json.Num) Json.parse("42")).value());
        assertEquals(-1, ((Json.Num) Json.parse("-1")).value());
        assertTrue(((Json.Bool) Json.parse("true")).value());
        assertTrue(Json.parse("null") instanceof Json.Null);
        assertEquals(0, ((Json.Array) Json.parse("[]")).size());
        assertEquals(0, ((Json.Object) Json.parse("{}")).members().size());
    }

    @Test
    void parsesNestedArrays() {
        Json.Array outer = (Json.Array) Json.parse("[[1,2],[3,4]]");
        assertEquals(2, outer.size());
        Json.Array inner = (Json.Array) outer.get(0);
        assertEquals(2, inner.size());
        assertEquals(2, ((Json.Num) inner.get(1)).value());
    }

    @Test
    void rejectsTrailingGarbage() {
        assertThrows(Json.JsonParseException.class, () -> Json.parse("{} x"));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(Json.JsonParseException.class, () -> Json.parse("\"abc"));
    }

    @Test
    void parsesRealisticPoolJson() {
        String poolJson = """
                {
                  "elements": [
                    {
                      "element": {
                        "element_type": "minecraft:single_pool_element",
                        "location": "minecraft:trial_chambers/corridor/atrium/bogged_relief",
                        "processors": { "processors": [] },
                        "projection": "rigid"
                      },
                      "weight": 1
                    }
                  ],
                  "fallback": "minecraft:empty"
                }
                """;
        Json.Object root = (Json.Object) Json.parse(poolJson);
        Json.Object element = (Json.Object) root.getArray("elements").get(0);
        assertEquals(1, element.getInt("weight"));
        Json.Object inner = (Json.Object) element.get("element");
        assertEquals("minecraft:single_pool_element", inner.getString("element_type"));
        assertEquals("minecraft:trial_chambers/corridor/atrium/bogged_relief", inner.getString("location"));
        assertEquals("rigid", inner.getString("projection"));
        assertEquals("minecraft:empty", root.getString("fallback"));
        // processors.processors 数组
        Json.Object processors = (Json.Object) inner.get("processors");
        assertEquals(0, processors.getArray("processors").size());
    }
}
