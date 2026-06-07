package com.twodcube.gtnhmcp.mcp.protocol;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An insertion-ordered collection of the {@link McpTool}s a server exposes.
 *
 * <p>
 * Insertion order is preserved so that {@code tools/list} output is deterministic — this keeps the protocol tests
 * stable
 * and gives the model a predictable tool ordering. Registration rejects duplicates and malformed tools eagerly so that
 * a
 * wiring mistake surfaces at startup rather than as a confusing runtime gap.
 */
public final class ToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<String, McpTool>();

    /**
     * Register a tool.
     *
     * @param tool the tool to add; its {@link McpTool#name()} must be non-empty and not already registered.
     * @throws IllegalArgumentException if the tool is null, has a blank name, or collides with an existing name.
     */
    public void register(McpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool must not be null");
        }
        String name = tool.name();
        if (name == null || name.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("duplicate tool name: " + name);
        }
        tools.put(name, tool);
    }

    /**
     * @param name tool name.
     * @return the registered tool, or {@code null} if no tool with that name exists.
     */
    public McpTool get(String name) {
        return tools.get(name);
    }

    /** @return all registered tools in registration order. */
    public Collection<McpTool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /** @return the number of registered tools. */
    public int size() {
        return tools.size();
    }
}
