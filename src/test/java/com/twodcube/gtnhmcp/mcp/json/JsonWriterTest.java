package com.twodcube.gtnhmcp.mcp.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonWriter}: scalar rendering, escaping, ordering, and round-tripping with {@link JsonParser}.
 */
class JsonWriterTest {

    @Test
    void writesScalars() {
        assertEquals("null", JsonWriter.write(null));
        assertEquals("true", JsonWriter.write(Boolean.TRUE));
        assertEquals("\"hi\"", JsonWriter.write("hi"));
        assertEquals("42", JsonWriter.write(Integer.valueOf(42)));
        assertEquals("42", JsonWriter.write(Long.valueOf(42)));
    }

    @Test
    void rendersWholeDoublesWithoutDecimalPoint() {
        assertEquals("5", JsonWriter.write(Double.valueOf(5.0)));
        assertEquals("5.5", JsonWriter.write(Double.valueOf(5.5)));
    }

    @Test
    void escapesStrings() {
        assertEquals("\"a\\\"b\"", JsonWriter.write("a\"b"));
        assertEquals("\"line\\n\\t\"", JsonWriter.write("line\n\t"));
        // Control characters below 0x20 become \\uXXXX escapes; build the char at runtime to avoid source escapes.
        String controlChar = String.valueOf((char) 1);
        assertEquals("\"\\u0001\"", JsonWriter.write(controlChar));
    }

    @Test
    void writesObjectInInsertionOrder() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("b", Integer.valueOf(1));
        map.put("a", "x");
        assertEquals("{\"b\":1,\"a\":\"x\"}", JsonWriter.write(map));
    }

    @Test
    void writesArrays() {
        List<Object> list = new ArrayList<Object>();
        list.add(Integer.valueOf(1));
        list.add("two");
        list.add(Boolean.FALSE);
        assertEquals("[1,\"two\",false]", JsonWriter.write(list));
    }

    @Test
    void rejectsUnsupportedTypesAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(Double.valueOf(Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(Double.valueOf(Double.POSITIVE_INFINITY)));
    }

    @Test
    void roundTripsThroughParser() {
        String json = "{\"name\":\"x\",\"n\":3,\"f\":1.25,\"ok\":true,\"items\":[1,2,3],\"nested\":{\"k\":\"v\"}}";
        Object parsed = JsonParser.parse(json);
        assertEquals(json, JsonWriter.write(parsed));
    }

    @Test
    void handlesNestedStructuresWithSpecialChars() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("path", "C:\\mods\\gt");
        map.put("quote", "say \"hi\"");
        String json = JsonWriter.write(map);
        Object back = JsonParser.parse(json);
        assertTrue(back instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> backMap = (Map<String, Object>) back;
        assertEquals("C:\\mods\\gt", backMap.get("path"));
        assertEquals("say \"hi\"", backMap.get("quote"));
    }
}
