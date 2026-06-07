package com.twodcube.gtnhmcp.client;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;

/**
 * A minimal, cached reflection helper used to read GregTech state without a compile-time dependency on GregTech.
 *
 * <p>
 * <b>Why reflection here?</b> Compiling directly against {@code MTEMultiBlockBase}/{@code IGregTechTileEntity} drags in
 * the transitive closure of their supertypes (GTNHLib, ModularUI, Waila, TecTech, ...), pinning this mod to one exact
 * GregTech build and to a large dependency tree. Reading the handful of long-stable members we need by name instead
 * lets
 * this mod (a) compile as a plain Minecraft mod, (b) work against whatever GregTech version the player actually has,
 * and
 * (c) be unit-tested against a fake "GregTech-shaped" object with no GregTech on the classpath.
 *
 * <p>
 * Every accessor converts reflective failures into a {@link McpToolException} with a precise message naming the missing
 * member, so a future GregTech rename surfaces as actionable text rather than an opaque stack trace. Resolved
 * {@link Method}/{@link Field} handles and {@link Class} lookups are cached because the diagnosis tools may run often.
 */
final class Reflect {

    private static final ConcurrentHashMap<String, Object> CLASS_CACHE = new ConcurrentHashMap<String, Object>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();

    /** Sentinel stored in {@link #CLASS_CACHE} to remember a negative lookup without re-attempting it. */
    private static final Object ABSENT = new Object();

    private Reflect() {}

    /**
     * Resolve a class by name, caching both hits and misses.
     *
     * @param fqn fully-qualified class name.
     * @return the {@link Class}, or {@code null} if it is not on the classpath.
     */
    static Class<?> optClass(String fqn) {
        Object cached = CLASS_CACHE.get(fqn);
        if (cached == ABSENT) {
            return null;
        }
        if (cached != null) {
            return (Class<?>) cached;
        }
        try {
            Class<?> resolved = Class.forName(fqn);
            CLASS_CACHE.put(fqn, resolved);
            return resolved;
        } catch (ClassNotFoundException e) {
            CLASS_CACHE.put(fqn, ABSENT);
            return null;
        }
    }

    /** @return true if {@code target} is an instance of the class named {@code fqn} (false if the class is absent). */
    static boolean isInstance(String fqn, Object target) {
        Class<?> type = optClass(fqn);
        return type != null && type.isInstance(target);
    }

    /**
     * Invoke a public no-argument method and return its raw result.
     *
     * @param target receiver object.
     * @param method method name.
     * @return the method's return value (may be null).
     * @throws McpToolException if the method is missing or throws.
     */
    static Object invoke(Object target, String method) throws McpToolException {
        Method m = resolveMethod(target.getClass(), method);
        try {
            return m.invoke(target);
        } catch (IllegalAccessException e) {
            throw new McpToolException(
                "Cannot access method '" + method
                    + "' on "
                    + target.getClass()
                        .getName(),
                e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new McpToolException("Method '" + method + "' failed: " + cause.getMessage(), cause);
        }
    }

    static boolean invokeBoolean(Object target, String method) throws McpToolException {
        return ((Boolean) invoke(target, method)).booleanValue();
    }

    static int invokeInt(Object target, String method) throws McpToolException {
        return ((Number) invoke(target, method)).intValue();
    }

    static long invokeLong(Object target, String method) throws McpToolException {
        return ((Number) invoke(target, method)).longValue();
    }

    static String invokeString(Object target, String method) throws McpToolException {
        Object value = invoke(target, method);
        return value == null ? null : value.toString();
    }

    static String[] invokeStringArray(Object target, String method) throws McpToolException {
        Object value = invoke(target, method);
        if (value == null) {
            return new String[0];
        }
        if (value instanceof String[]) {
            return (String[]) value;
        }
        throw new McpToolException("Method '" + method + "' did not return a String[]");
    }

    /**
     * Read a public boolean field.
     *
     * @param target receiver object.
     * @param field  field name.
     * @return the field value.
     * @throws McpToolException if the field is missing or inaccessible.
     */
    static boolean getBooleanField(Object target, String field) throws McpToolException {
        try {
            return resolveField(target.getClass(), field).getBoolean(target);
        } catch (IllegalAccessException e) {
            throw new McpToolException(
                "Cannot access field '" + field
                    + "' on "
                    + target.getClass()
                        .getName(),
                e);
        }
    }

    /**
     * Read a public int field.
     *
     * @param target receiver object.
     * @param field  field name.
     * @return the field value.
     * @throws McpToolException if the field is missing or inaccessible.
     */
    static int getIntField(Object target, String field) throws McpToolException {
        try {
            return resolveField(target.getClass(), field).getInt(target);
        } catch (IllegalAccessException e) {
            throw new McpToolException(
                "Cannot access field '" + field
                    + "' on "
                    + target.getClass()
                        .getName(),
                e);
        }
    }

    private static Method resolveMethod(Class<?> type, String name) throws McpToolException {
        String key = type.getName() + "#" + name;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Method m = type.getMethod(name);
            m.setAccessible(true);
            METHOD_CACHE.put(key, m);
            return m;
        } catch (NoSuchMethodException e) {
            throw new McpToolException(
                "Incompatible GregTech version: method '" + name + "' not found on " + type.getName(),
                e);
        }
    }

    private static Field resolveField(Class<?> type, String name) throws McpToolException {
        String key = type.getName() + "." + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Field f = type.getField(name);
            f.setAccessible(true);
            FIELD_CACHE.put(key, f);
            return f;
        } catch (NoSuchFieldException e) {
            throw new McpToolException(
                "Incompatible GregTech version: field '" + name + "' not found on " + type.getName(),
                e);
        }
    }
}
