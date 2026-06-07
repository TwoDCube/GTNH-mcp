package com.twodcube.gtnhmcp.mcp.protocol;

import java.util.Map;

/**
 * A single capability exposed to the model over MCP {@code tools/list} and invoked via {@code tools/call}.
 *
 * <p>
 * Tools in this mod are read-only introspectors of the running Minecraft client. Implementations that touch game state
 * MUST marshal that work onto the client thread themselves (see
 * {@code com.twodcube.gtnhmcp.client.ClientThreadExecutor})
 * — {@link #call} is invoked on an HTTP worker thread, never on the Minecraft thread. The protocol layer that drives
 * this
 * interface has no Minecraft dependency, which is what lets the dispatcher be unit-tested without a running game.
 */
public interface McpTool {

    /**
     * @return the unique tool name (the identifier the model calls). Stable, lowercase-with-underscores by convention,
     *         e.g. {@code diagnose_multiblock}.
     */
    String name();

    /** @return a short human-friendly title shown in tool listings, e.g. "Diagnose GregTech multiblock". */
    String title();

    /**
     * @return a model-facing description of what the tool does and when to use it. This is the primary signal the model
     *         uses to decide whether to call the tool, so it should be specific and example-driven.
     */
    String description();

    /**
     * @return a JSON Schema object (as the plain-map JSON model) describing {@code arguments}. Must be a schema of
     *         {@code "type":"object"} with a {@code properties} map (possibly empty) — this is what MCP clients
     *         validate
     *         arguments against and render as the tool's parameter form.
     */
    Map<String, Object> inputSchema();

    /**
     * Execute the tool.
     *
     * @param arguments the caller-supplied arguments, already parsed from JSON into the plain-map model (never null;
     *                  empty map when the caller sent none). Implementations must validate their own arguments.
     * @return the result to return to the model.
     * @throws McpToolException if the call cannot be fulfilled; the dispatcher converts this into an {@code isError}
     *                          result whose text is the exception message.
     */
    ToolResult call(Map<String, Object> arguments) throws McpToolException;
}
