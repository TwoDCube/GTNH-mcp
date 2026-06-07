package com.twodcube.gtnhmcp.mcp.protocol;

/**
 * Thrown by an {@link McpTool} when a call cannot be fulfilled (bad arguments, nothing to inspect, game not ready,
 * ...).
 *
 * <p>
 * Per the MCP specification, errors that occur <em>inside</em> a tool are reported back to the model as a normal
 * {@code tools/call} result with {@code isError: true} — not as a JSON-RPC protocol error. {@link McpDispatcher}
 * catches
 * this exception and turns its message into that error result, so the message text is read by the model and should be
 * phrased as actionable guidance ("Look at a block first", "No multiblock controller at the target").
 */
public class McpToolException extends Exception {

    private static final long serialVersionUID = 1L;

    /** @param message operator/model-facing description of what went wrong and, ideally, how to fix it. */
    public McpToolException(String message) {
        super(message);
    }

    /**
     * @param message operator/model-facing description of what went wrong.
     * @param cause   the underlying failure, preserved for server-side logging.
     */
    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
