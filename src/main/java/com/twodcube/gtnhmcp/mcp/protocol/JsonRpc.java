package com.twodcube.gtnhmcp.mcp.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Constants and tiny builders for JSON-RPC 2.0 messages, which is the wire framing MCP rides on.
 *
 * <p>
 * Only the subset this server needs is modelled. The error codes are the standard JSON-RPC ones (see
 * <a href="https://www.jsonrpc.org/specification">the spec</a>); MCP layers its own semantics on top but reuses these
 * codes for transport-level failures.
 */
public final class JsonRpc {

    /** The only JSON-RPC version MCP uses. */
    public static final String VERSION = "2.0";

    /** Invalid JSON was received by the server. */
    public static final int PARSE_ERROR = -32700;
    /** The JSON sent is not a valid Request object. */
    public static final int INVALID_REQUEST = -32600;
    /** The method does not exist / is not available. */
    public static final int METHOD_NOT_FOUND = -32601;
    /** Invalid method parameter(s). */
    public static final int INVALID_PARAMS = -32602;
    /** Internal JSON-RPC error. */
    public static final int INTERNAL_ERROR = -32603;

    private JsonRpc() {}

    /**
     * Build a successful response object.
     *
     * @param id     the request id to echo (may be null only for the unusual null-id request).
     * @param result the result payload.
     * @return a mutable map representing the JSON-RPC response.
     */
    public static Map<String, Object> result(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", VERSION);
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    /**
     * Build an error response object.
     *
     * @param id      the request id to echo, or null when the id could not be determined (e.g. parse error).
     * @param code    a JSON-RPC error code.
     * @param message a short human-readable error description.
     * @return a mutable map representing the JSON-RPC error response.
     */
    public static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<String, Object>();
        err.put("code", Integer.valueOf(code));
        err.put("message", message);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", VERSION);
        response.put("id", id);
        response.put("error", err);
        return response;
    }
}
