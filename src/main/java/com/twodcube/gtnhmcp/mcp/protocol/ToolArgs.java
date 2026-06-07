package com.twodcube.gtnhmcp.mcp.protocol;

import java.util.List;
import java.util.Map;

/**
 * A small, null-safe accessor for tool argument maps that raises {@link McpToolException} with a clear message on every
 * boundary violation, instead of leaking a bare {@link ClassCastException} or {@link NullPointerException} to the
 * model.
 *
 * <p>
 * Argument values follow the plain-Java JSON model (see {@code com.twodcube.gtnhmcp.mcp.json.JsonParser}): JSON numbers
 * arrive as {@link Long} or {@link Double}, so the integer accessors here accept any {@link Number} and range-check it.
 * This keeps every tool's input validation consistent and honors the project rule that boundary inputs fail with
 * operator-actionable errors.
 */
public final class ToolArgs {

    private final Map<String, Object> args;

    /**
     * @param args the raw arguments map; {@code null} is treated as "no arguments supplied".
     */
    public ToolArgs(Map<String, Object> args) {
        this.args = args;
    }

    /** @return true if the named argument was supplied with a non-null value. */
    public boolean has(String key) {
        return args != null && args.get(key) != null;
    }

    /**
     * @param key argument name.
     * @return the string value of a required argument.
     * @throws McpToolException if the argument is missing or is not a string.
     */
    public String requireString(String key) throws McpToolException {
        Object v = require(key);
        if (!(v instanceof String)) {
            throw new McpToolException("argument '" + key + "' must be a string");
        }
        return (String) v;
    }

    /**
     * @param key          argument name.
     * @param defaultValue value to return when the argument is absent.
     * @return the string value, or {@code defaultValue} when absent.
     * @throws McpToolException if the argument is present but not a string.
     */
    public String optString(String key, String defaultValue) throws McpToolException {
        if (!has(key)) {
            return defaultValue;
        }
        Object v = args.get(key);
        if (!(v instanceof String)) {
            throw new McpToolException("argument '" + key + "' must be a string");
        }
        return (String) v;
    }

    /**
     * Read an integer argument, clamping nothing but validating type and range.
     *
     * @param key          argument name.
     * @param defaultValue value to return when the argument is absent.
     * @param min          inclusive lower bound.
     * @param max          inclusive upper bound.
     * @return the integer value, or {@code defaultValue} when absent.
     * @throws McpToolException if present but not an integral number, or outside [{@code min}, {@code max}].
     */
    public int optInt(String key, int defaultValue, int min, int max) throws McpToolException {
        if (!has(key)) {
            return defaultValue;
        }
        Object v = args.get(key);
        if (!(v instanceof Number)) {
            throw new McpToolException("argument '" + key + "' must be a number");
        }
        double d = ((Number) v).doubleValue();
        if (d != Math.floor(d)) {
            throw new McpToolException("argument '" + key + "' must be a whole number");
        }
        long asLong = (long) d;
        if (asLong < min || asLong > max) {
            throw new McpToolException("argument '" + key + "' must be between " + min + " and " + max);
        }
        return (int) asLong;
    }

    /**
     * @param key          argument name.
     * @param defaultValue value to return when the argument is absent.
     * @return the boolean value, or {@code defaultValue} when absent.
     * @throws McpToolException if present but not a boolean.
     */
    public boolean optBoolean(String key, boolean defaultValue) throws McpToolException {
        if (!has(key)) {
            return defaultValue;
        }
        Object v = args.get(key);
        if (!(v instanceof Boolean)) {
            throw new McpToolException("argument '" + key + "' must be a boolean");
        }
        return ((Boolean) v).booleanValue();
    }

    /**
     * Read a required 3-element integer coordinate array, e.g. {@code "pos": [x, y, z]}.
     *
     * @param key argument name.
     * @return an {@code int[3]} of x, y, z.
     * @throws McpToolException if the argument is missing, not a 3-element array, or contains non-integers.
     */
    public int[] requireIntTriple(String key) throws McpToolException {
        Object v = require(key);
        if (!(v instanceof List)) {
            throw new McpToolException("argument '" + key + "' must be an array of three integers [x, y, z]");
        }
        List<?> list = (List<?>) v;
        if (list.size() != 3) {
            throw new McpToolException("argument '" + key + "' must contain exactly three integers [x, y, z]");
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            Object e = list.get(i);
            if (!(e instanceof Number)) {
                throw new McpToolException("argument '" + key + "'[" + i + "] must be a number");
            }
            double d = ((Number) e).doubleValue();
            if (d != Math.floor(d)) {
                throw new McpToolException("argument '" + key + "'[" + i + "] must be a whole number");
            }
            out[i] = (int) (long) d;
        }
        return out;
    }

    private Object require(String key) throws McpToolException {
        if (!has(key)) {
            throw new McpToolException("missing required argument '" + key + "'");
        }
        return args.get(key);
    }
}
