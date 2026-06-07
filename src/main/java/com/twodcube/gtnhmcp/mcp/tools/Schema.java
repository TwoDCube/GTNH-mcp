package com.twodcube.gtnhmcp.mcp.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny builders for the JSON Schema fragments that describe each tool's arguments.
 *
 * <p>
 * MCP clients render and validate {@code tools/call} arguments against the {@code inputSchema} a tool advertises, so
 * the
 * schemas need to be correct and self-describing. These helpers keep that construction readable instead of burying tool
 * code in nested map literals. Everything is emitted as the plain-map JSON model consumed by
 * {@code com.twodcube.gtnhmcp.mcp.json.JsonWriter}.
 */
final class Schema {

    private Schema() {}

    /**
     * Build a top-level object schema.
     *
     * @param properties ordered map of property name to its sub-schema (use {@link #props()}).
     * @param required   names of required properties.
     * @return an object schema map.
     */
    static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", new ArrayList<Object>(Arrays.asList(required)));
        }
        // Reject unexpected keys so typos surface as validation errors rather than being silently ignored.
        schema.put("additionalProperties", Boolean.FALSE);
        return schema;
    }

    /** @return a fresh, insertion-ordered property map to populate and pass to {@link #object}. */
    static Map<String, Object> props() {
        return new LinkedHashMap<String, Object>();
    }

    /**
     * @param description human-facing description of the property.
     * @return a string-typed property schema.
     */
    static Map<String, Object> string(String description) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "string");
        p.put("description", description);
        return p;
    }

    /**
     * @param description human-facing description.
     * @param min         inclusive minimum.
     * @param max         inclusive maximum.
     * @return an integer-typed property schema with bounds.
     */
    static Map<String, Object> integer(String description, int min, int max) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "integer");
        p.put("description", description);
        p.put("minimum", Integer.valueOf(min));
        p.put("maximum", Integer.valueOf(max));
        return p;
    }

    /**
     * @param description human-facing description.
     * @return a boolean-typed property schema.
     */
    static Map<String, Object> bool(String description) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "boolean");
        p.put("description", description);
        return p;
    }

    /**
     * Build a fixed-length integer coordinate array schema, e.g. {@code [x, y, z]}.
     *
     * @param description human-facing description.
     * @return an array schema of exactly three integers.
     */
    static Map<String, Object> intTriple(String description) {
        Map<String, Object> items = new LinkedHashMap<String, Object>();
        items.put("type", "integer");
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "array");
        p.put("description", description);
        p.put("items", items);
        p.put("minItems", Integer.valueOf(3));
        p.put("maxItems", Integer.valueOf(3));
        return p;
    }

    /** @return an empty object schema (for tools that take no arguments). */
    static Map<String, Object> noArgs() {
        return object(props());
    }

    /** Convenience to keep tool code terse when assembling a property map. */
    static Map<String, Object> with(Map<String, Object> map, String key, Map<String, Object> value) {
        map.put(key, value);
        return map;
    }
}
