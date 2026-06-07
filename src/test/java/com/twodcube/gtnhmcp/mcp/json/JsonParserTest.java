package com.twodcube.gtnhmcp.mcp.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link JsonParser}: type mapping, escapes, numbers, and error reporting. */
class JsonParserTest {

    @Test
    void parsesScalars() {
        assertEquals("hi", JsonParser.parse("\"hi\""));
        assertEquals(Long.valueOf(42), JsonParser.parse("42"));
        assertEquals(Long.valueOf(-7), JsonParser.parse("-7"));
        assertEquals(Double.valueOf(3.5), JsonParser.parse("3.5"));
        assertEquals(Double.valueOf(1.0e3), JsonParser.parse("1e3"));
        assertEquals(Boolean.TRUE, JsonParser.parse("true"));
        assertEquals(Boolean.FALSE, JsonParser.parse("false"));
        assertNull(JsonParser.parse("null"));
    }

    @Test
    void integralNumbersBecomeLongAndFractionsBecomeDouble() {
        assertTrue(JsonParser.parse("100") instanceof Long);
        assertTrue(JsonParser.parse("100.0") instanceof Double);
        assertTrue(JsonParser.parse("1.5e2") instanceof Double);
    }

    @Test
    void parsesObjectPreservingOrder() {
        Object parsed = JsonParser.parse("{\"b\":1,\"a\":2}");
        assertTrue(parsed instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals(2, map.size());
        assertEquals(
            "[b, a]",
            map.keySet()
                .toString());
        assertEquals(Long.valueOf(1), map.get("b"));
        assertEquals(Long.valueOf(2), map.get("a"));
    }

    @Test
    void parsesNestedArraysAndObjects() {
        Object parsed = JsonParser.parse("{\"items\":[1,{\"x\":true},\"z\"]}");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) map.get("items");
        assertEquals(3, items.size());
        assertEquals(Long.valueOf(1), items.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) items.get(1);
        assertEquals(Boolean.TRUE, inner.get("x"));
        assertEquals("z", items.get(2));
    }

    @Test
    void parsesStringEscapesIncludingUnicode() {
        assertEquals("a\"b\\c/d", JsonParser.parse("\"a\\\"b\\\\c\\/d\""));
        assertEquals("line1\nline2\t!", JsonParser.parse("\"line1\\nline2\\t!\""));
        assertEquals("é", JsonParser.parse("\"\\u00e9\""));
    }

    @Test
    void emptyContainers() {
        assertTrue(((Map<?, ?>) JsonParser.parse("{}")).isEmpty());
        assertTrue(((List<?>) JsonParser.parse("[]")).isEmpty());
    }

    @Test
    void rejectsTrailingContent() {
        JsonException e = assertThrows(JsonException.class, () -> JsonParser.parse("{} extra"));
        assertTrue(
            e.getMessage()
                .contains("trailing"));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(JsonException.class, () -> JsonParser.parse(""));
        assertThrows(JsonException.class, () -> JsonParser.parse("{\"a\":}"));
        assertThrows(JsonException.class, () -> JsonParser.parse("[1,]"));
        assertThrows(JsonException.class, () -> JsonParser.parse("\"unterminated"));
        assertThrows(JsonException.class, () -> JsonParser.parse("nul"));
        assertThrows(JsonException.class, () -> JsonParser.parse("{\"a\" 1}"));
    }

    @Test
    void nullInputThrows() {
        assertThrows(JsonException.class, () -> JsonParser.parse(null));
    }
}
