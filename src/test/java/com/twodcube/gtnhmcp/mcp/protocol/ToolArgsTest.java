package com.twodcube.gtnhmcp.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ToolArgs} validation and type coercion against the plain-map JSON model. */
class ToolArgsTest {

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void hasReflectsPresenceAndNonNull() {
        ToolArgs a = new ToolArgs(args("x", "v", "n", null));
        assertTrue(a.has("x"));
        assertFalse(a.has("n"));
        assertFalse(a.has("absent"));
        assertFalse(new ToolArgs(null).has("anything"));
    }

    @Test
    void requireStringValidates() throws McpToolException {
        assertEquals("v", new ToolArgs(args("k", "v")).requireString("k"));
        assertThrows(McpToolException.class, () -> new ToolArgs(args()).requireString("k"));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("k", Integer.valueOf(1))).requireString("k"));
    }

    @Test
    void optStringDefaultsAndValidates() throws McpToolException {
        assertEquals("d", new ToolArgs(args()).optString("k", "d"));
        assertEquals("v", new ToolArgs(args("k", "v")).optString("k", "d"));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("k", Boolean.TRUE)).optString("k", "d"));
    }

    @Test
    void optIntAcceptsJsonNumbersAndEnforcesRange() throws McpToolException {
        // JSON integers arrive as Long.
        assertEquals(7, new ToolArgs(args("k", Long.valueOf(7))).optInt("k", 1, 0, 10));
        // A whole-valued Double is accepted.
        assertEquals(4, new ToolArgs(args("k", Double.valueOf(4.0))).optInt("k", 1, 0, 10));
        assertEquals(3, new ToolArgs(args()).optInt("k", 3, 0, 10));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("k", Double.valueOf(2.5))).optInt("k", 1, 0, 10));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("k", Long.valueOf(99))).optInt("k", 1, 0, 10));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("k", "x")).optInt("k", 1, 0, 10));
    }

    @Test
    void optBooleanValidates() throws McpToolException {
        assertTrue(new ToolArgs(args("k", Boolean.TRUE)).optBoolean("k", false));
        assertFalse(new ToolArgs(args()).optBoolean("k", false));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("k", "true")).optBoolean("k", false));
    }

    @Test
    void requireIntTripleValidates() throws McpToolException {
        List<Object> triple = new ArrayList<Object>();
        triple.add(Long.valueOf(1));
        triple.add(Long.valueOf(-2));
        triple.add(Double.valueOf(3.0));
        assertArrayEquals(new int[] { 1, -2, 3 }, new ToolArgs(args("pos", triple)).requireIntTriple("pos"));

        assertThrows(McpToolException.class, () -> new ToolArgs(args()).requireIntTriple("pos"));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("pos", "x")).requireIntTriple("pos"));

        List<Object> two = new ArrayList<Object>();
        two.add(Long.valueOf(1));
        two.add(Long.valueOf(2));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("pos", two)).requireIntTriple("pos"));

        List<Object> fractional = new ArrayList<Object>();
        fractional.add(Double.valueOf(1.5));
        fractional.add(Long.valueOf(2));
        fractional.add(Long.valueOf(3));
        assertThrows(McpToolException.class, () -> new ToolArgs(args("pos", fractional)).requireIntTriple("pos"));
    }
}
