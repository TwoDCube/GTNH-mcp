package com.twodcube.gtnhmcp.mcp.protocol;

import java.util.Collections;
import java.util.Map;

import com.twodcube.gtnhmcp.mcp.json.JsonWriter;

/**
 * The outcome of an {@link McpTool} call, before it is wrapped into the MCP {@code tools/call} result envelope.
 *
 * <p>
 * Every result carries human/model-readable {@code text} (always present) and optionally a {@code structured} map. When
 * a tool produces structured data we serialize it to JSON for the {@code text} block (so models without
 * structured-output
 * support still see everything) <em>and</em> expose it as MCP {@code structuredContent}. {@code isError} distinguishes
 * a
 * successful inspection from a recoverable failure the model should read and react to.
 */
public final class ToolResult {

    private final String text;
    private final Map<String, Object> structured;
    private final boolean isError;

    private ToolResult(String text, Map<String, Object> structured, boolean isError) {
        this.text = text;
        this.structured = structured;
        this.isError = isError;
    }

    /**
     * Build a successful structured result. The map is serialized to compact JSON for the text block and also surfaced
     * as MCP {@code structuredContent}.
     *
     * @param data the structured payload; must not be null.
     * @return a non-error result.
     */
    public static ToolResult data(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return new ToolResult(JsonWriter.write(data), data, false);
    }

    /**
     * Build a successful plain-text result (no structured payload).
     *
     * @param text the text content; must not be null.
     * @return a non-error result.
     */
    public static ToolResult text(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        return new ToolResult(text, null, false);
    }

    /**
     * Build an error result that the model will read as {@code isError: true}.
     *
     * @param message description of the failure; must not be null.
     * @return an error result.
     */
    public static ToolResult error(String message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return new ToolResult(message, null, true);
    }

    /** @return the always-present text content. */
    public String text() {
        return text;
    }

    /** @return the structured payload, or {@code null} if this result is plain text. */
    public Map<String, Object> structured() {
        return structured == null ? null : Collections.unmodifiableMap(structured);
    }

    /** @return whether this result represents a tool-level error. */
    public boolean isError() {
        return isError;
    }
}
