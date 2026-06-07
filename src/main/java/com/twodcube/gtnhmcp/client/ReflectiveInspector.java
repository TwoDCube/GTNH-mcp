package com.twodcube.gtnhmcp.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic, <b>read-only</b> reflection dump of an arbitrary object: every field (including private/inherited) and the
 * result of every no-argument getter-style method.
 *
 * <p>
 * This exists so an operator can read state the bespoke tools don't expose (e.g. a multiblock's {@code motorTier},
 * {@code isAllowedToWork()}, or a module list) without a new field being hand-added each time. It is deliberately
 * read-only:
 * <ul>
 * <li>Field reads have no side effects.</li>
 * <li>Only methods whose names match a getter allowlist ({@code get/is/has/can/are/count/size}) and take no arguments
 * are invoked. That excludes the dangerous no-arg mutators on these tiles — {@code connect()}, {@code disconnect()},
 * {@code stopMachine()}, {@code explodeMultiblock()}, etc. — so a dump can never change game state.</li>
 * </ul>
 *
 * <p>
 * Output is bounded (field/method counts, collection sizes, string length, and nesting depth are all capped) and is
 * always composed of JSON-serializable types ({@link Map}, {@link List}, {@link Number}, {@link Boolean},
 * {@link String}, or {@code null}), so it serializes cleanly. The class has no Minecraft dependency, which keeps it
 * unit-testable.
 */
final class ReflectiveInspector {

    /** Maximum nesting depth when expanding nested collections/arrays/maps before falling back to a summary. */
    static final int MAX_DEPTH = 3;
    /** Maximum number of elements expanded from any one collection/array/map. */
    static final int MAX_ELEMENTS = 64;
    /** Maximum length of any single rendered string before truncation. */
    static final int MAX_STRING = 512;
    /** Maximum number of fields dumped per object. */
    static final int MAX_FIELDS = 250;
    /** Maximum number of getter methods invoked per object. */
    static final int MAX_METHODS = 250;

    private static final String[] GETTER_PREFIXES = { "get", "is", "has", "can", "are", "count", "size" };

    private ReflectiveInspector() {}

    /**
     * Produce a read-only dump of {@code target}.
     *
     * @param target the object to inspect; {@code null} yields a map with a null {@code class}.
     * @return a map with {@code class}, {@code fields} (name &rarr; serialized value) and {@code getters} (method name
     *         &rarr; serialized result).
     */
    static Map<String, Object> inspect(Object target) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (target == null) {
            result.put("class", null);
            return result;
        }
        result.put(
            "class",
            target.getClass()
                .getName());
        result.put("fields", dumpFields(target));
        result.put("getters", dumpGetters(target));
        return result;
    }

    private static Map<String, Object> dumpFields(Object target) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        int count = 0;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic()) {
                    continue;
                }
                String name = f.getName();
                if (out.containsKey(name)) {
                    continue; // a subclass field already shadowed this name
                }
                if (count >= MAX_FIELDS) {
                    out.put("__truncated__", Boolean.TRUE);
                    return out;
                }
                count++;
                try {
                    f.setAccessible(true);
                    out.put(name, serialize(f.get(target), 0));
                } catch (Throwable t) {
                    out.put(
                        name,
                        "<unreadable: " + t.getClass()
                            .getSimpleName() + ">");
                }
            }
        }
        return out;
    }

    private static Map<String, Object> dumpGetters(Object target) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        int count = 0;
        for (Method m : target.getClass()
            .getMethods()) {
            if (m.getParameterTypes().length != 0 || m.getReturnType() == void.class) {
                continue;
            }
            String name = m.getName();
            if ("getClass".equals(name) || !isGetterName(name) || out.containsKey(name)) {
                continue;
            }
            if (count >= MAX_METHODS) {
                out.put("__truncated__", Boolean.TRUE);
                break;
            }
            count++;
            try {
                m.setAccessible(true);
                out.put(name, serialize(m.invoke(target), 0));
            } catch (Throwable t) {
                Throwable cause = t.getCause() == null ? t : t.getCause();
                out.put(
                    name,
                    "<threw: " + cause.getClass()
                        .getSimpleName() + ">");
            }
        }
        return out;
    }

    /** @return whether {@code name} starts with a getter prefix (and so is presumed side-effect-free). */
    static boolean isGetterName(String name) {
        for (String prefix : GETTER_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Serialize an arbitrary value into JSON-friendly types, bounded by depth/size.
     *
     * @param value the value to serialize.
     * @param depth current nesting depth.
     * @return a {@link Map}, {@link List}, {@link Number}, {@link Boolean}, {@link String}, or {@code null}.
     */
    static Object serialize(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return truncate(value.toString());
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        if (value.getClass()
            .isArray()) {
            return serializeArray(value, depth);
        }
        if (value instanceof Collection) {
            return serializeCollection((Collection<?>) value, depth);
        }
        if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value, depth);
        }
        // Any other object: a bounded, side-effect-light textual summary (never recurse into its fields).
        return truncate(safeToString(value));
    }

    private static Object serializeArray(Object array, int depth) {
        int length = Array.getLength(array);
        if (depth >= MAX_DEPTH) {
            return "<" + array.getClass()
                .getSimpleName() + " length=" + length + ">";
        }
        List<Object> out = new java.util.ArrayList<Object>();
        int limit = Math.min(length, MAX_ELEMENTS);
        for (int i = 0; i < limit; i++) {
            out.add(serialize(Array.get(array, i), depth + 1));
        }
        if (length > limit) {
            out.add("<" + (length - limit) + " more>");
        }
        return out;
    }

    private static Object serializeCollection(Collection<?> collection, int depth) {
        int size = collection.size();
        if (depth >= MAX_DEPTH) {
            return "<" + collection.getClass()
                .getSimpleName() + " size=" + size + ">";
        }
        List<Object> out = new java.util.ArrayList<Object>();
        int i = 0;
        for (Object element : collection) {
            if (i >= MAX_ELEMENTS) {
                out.add("<" + (size - i) + " more>");
                break;
            }
            out.add(serialize(element, depth + 1));
            i++;
        }
        return out;
    }

    private static Object serializeMap(Map<?, ?> map, int depth) {
        if (depth >= MAX_DEPTH) {
            return "<Map size=" + map.size() + ">";
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (i >= MAX_ELEMENTS) {
                out.put("__truncated__", Boolean.TRUE);
                break;
            }
            out.put(truncate(String.valueOf(entry.getKey())), serialize(entry.getValue(), depth + 1));
            i++;
        }
        return out;
    }

    private static String safeToString(Object value) {
        try {
            return value.toString();
        } catch (Throwable t) {
            return "<" + value.getClass()
                .getName() + ">";
        }
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_STRING) {
            return s;
        }
        return s.substring(0, MAX_STRING) + "…(" + (s.length() - MAX_STRING) + " more chars)";
    }
}
