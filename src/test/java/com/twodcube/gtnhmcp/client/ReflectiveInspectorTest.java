package com.twodcube.gtnhmcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReflectiveInspector}: field/getter dumping, the read-only getter allowlist, and bounded
 * serialization — all against a plain fake object, no Minecraft required.
 */
class ReflectiveInspectorTest {

    enum Color {
        RED,
        GREEN
    }

    /** A fake "tile-like" object exposing private/public fields, getters, a thrower, and a mutator-named method. */
    public static final class Fake {

        private final int secret = 42;
        public String label = "hi";
        public List<String> items = Arrays.asList("a", "b");
        public Color color = Color.GREEN;

        public boolean isReady() {
            return true;
        }

        public int getCount() {
            return 7;
        }

        public String getName() {
            return "Widget";
        }

        public Object getThing() {
            throw new IllegalStateException("nope");
        }

        // Not a getter name -> must NOT be invoked/listed.
        public String connect() {
            return "SHOULD_NOT_APPEAR";
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void dumpsFieldsIncludingPrivate() {
        Map<String, Object> dump = ReflectiveInspector.inspect(new Fake());
        assertEquals(Fake.class.getName(), dump.get("class"));

        Map<String, Object> fields = (Map<String, Object>) dump.get("fields");
        assertEquals(42, ((Number) fields.get("secret")).intValue());
        assertEquals("hi", fields.get("label"));
        assertEquals("GREEN", fields.get("color")); // enum -> name
        assertInstanceOf(List.class, fields.get("items"));
        assertEquals(Arrays.asList("a", "b"), fields.get("items"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invokesGetterAllowlistOnlyAndHandlesThrows() {
        Map<String, Object> dump = ReflectiveInspector.inspect(new Fake());
        Map<String, Object> getters = (Map<String, Object>) dump.get("getters");

        assertEquals(Boolean.TRUE, getters.get("isReady"));
        assertEquals(7, ((Number) getters.get("getCount")).intValue());
        assertEquals("Widget", getters.get("getName"));
        // A throwing getter is reported, not propagated.
        assertTrue(
            getters.get("getThing")
                .toString()
                .contains("threw"));
        // Mutator-named and Object.getClass must never be invoked/listed.
        assertFalse(getters.containsKey("connect"), "non-getter-named methods must not be invoked");
        assertFalse(getters.containsKey("getClass"), "getClass must be excluded");
    }

    @Test
    void getterNameAllowlist() {
        assertTrue(ReflectiveInspector.isGetterName("getX"));
        assertTrue(ReflectiveInspector.isGetterName("isActive"));
        assertTrue(ReflectiveInspector.isGetterName("hasPower"));
        assertTrue(ReflectiveInspector.isGetterName("canRun"));
        assertTrue(ReflectiveInspector.isGetterName("sizeOf"));
        assertFalse(ReflectiveInspector.isGetterName("connect"));
        assertFalse(ReflectiveInspector.isGetterName("stopMachine"));
        assertFalse(ReflectiveInspector.isGetterName("explodeMultiblock"));
    }

    @Test
    void serializeProducesJsonFriendlyTypes() {
        assertNull(ReflectiveInspector.serialize(null, 0));
        assertEquals(Boolean.TRUE, ReflectiveInspector.serialize(Boolean.TRUE, 0));
        assertEquals(5L, ReflectiveInspector.serialize(Long.valueOf(5), 0));
        assertEquals("RED", ReflectiveInspector.serialize(Color.RED, 0));
        assertEquals("x", ReflectiveInspector.serialize("x", 0));
        // Primitive arrays handled.
        Object arr = ReflectiveInspector.serialize(new int[] { 1, 2, 3 }, 0);
        assertInstanceOf(List.class, arr);
        assertEquals(3, ((List<?>) arr).size());
    }

    @Test
    void truncatesLongStrings() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ReflectiveInspector.MAX_STRING + 50; i++) {
            sb.append('a');
        }
        String result = (String) ReflectiveInspector.serialize(sb.toString(), 0);
        assertTrue(result.length() < sb.length());
        assertTrue(result.contains("more chars"));
    }

    @Test
    void inspectNullYieldsNullClass() {
        Map<String, Object> dump = ReflectiveInspector.inspect(null);
        assertNull(dump.get("class"));
    }
}
