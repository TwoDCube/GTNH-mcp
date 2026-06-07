package com.twodcube.gtnhmcp.mcp.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.twodcube.gtnhmcp.mcp.json.JsonException;
import com.twodcube.gtnhmcp.mcp.json.JsonParser;
import com.twodcube.gtnhmcp.mcp.json.JsonWriter;

/**
 * The transport-agnostic heart of the MCP server: it turns one inbound JSON-RPC message into the outbound JSON-RPC
 * message (or into "no response" for notifications), with no knowledge of HTTP or Minecraft.
 *
 * <p>
 * It implements the request/response subset of the MCP specification that a read-only tool server needs:
 * <ul>
 * <li>{@code initialize} — version negotiation + capability/serverInfo advertisement</li>
 * <li>{@code notifications/*} — accepted silently (no response, per JSON-RPC notification semantics)</li>
 * <li>{@code ping} — liveness check</li>
 * <li>{@code tools/list} — enumerate registered {@link McpTool}s</li>
 * <li>{@code tools/call} — invoke a tool, mapping tool-level failures to {@code isError} results and protocol misuse to
 * JSON-RPC errors</li>
 * </ul>
 *
 * <p>
 * Keeping this class free of HTTP and Minecraft is deliberate: the entire protocol can be exercised in plain JUnit by
 * feeding {@link #handleRaw(String)} request strings and asserting on the response strings, which is how this project
 * meets its "tested without live services" bar.
 */
public final class McpDispatcher {

    /**
     * MCP protocol versions we will echo back during {@code initialize} when a client requests them. We implement only
     * the request/response subset, which is stable across all of these, so echoing the client's version maximizes
     * compatibility. Anything outside this set negotiates down to {@link #defaultProtocolVersion}.
     */
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = new HashSet<String>(
        Arrays.asList("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25"));

    private final String serverName;
    private final String serverVersion;
    private final String defaultProtocolVersion;
    private final String instructions;
    private final ToolRegistry registry;

    /**
     * @param serverName             advertised {@code serverInfo.name}.
     * @param serverVersion          advertised {@code serverInfo.version}.
     * @param defaultProtocolVersion the version to negotiate to when the client requests an unknown one.
     * @param instructions           free-text usage guidance returned from {@code initialize} (may be null).
     * @param registry               the tools to expose; must not be null.
     */
    public McpDispatcher(String serverName, String serverVersion, String defaultProtocolVersion, String instructions,
        ToolRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.defaultProtocolVersion = defaultProtocolVersion;
        this.instructions = instructions;
        this.registry = registry;
    }

    /**
     * Handle one raw JSON-RPC message body and produce the raw response body.
     *
     * @param body the request body text (a single JSON-RPC object).
     * @return the JSON response text, or an empty string when no response is due (the message was a notification). The
     *         HTTP layer maps the empty string to a {@code 202 Accepted} with no body.
     */
    public String handleRaw(String body) {
        Object parsed;
        try {
            parsed = JsonParser.parse(body);
        } catch (JsonException e) {
            return JsonWriter.write(JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Parse error: " + e.getMessage()));
        }
        if (!(parsed instanceof Map)) {
            // JSON-RPC batching (a top-level array) was removed in MCP 2025-06-18 and is not supported here.
            return JsonWriter
                .write(JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Expected a single JSON-RPC request object"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) parsed;
        Map<String, Object> response;
        try {
            response = handleMessage(request);
        } catch (RuntimeException unexpected) {
            return JsonWriter.write(
                JsonRpc.error(request.get("id"), JsonRpc.INTERNAL_ERROR, "Internal error: " + unexpected.getMessage()));
        }
        return response == null ? "" : JsonWriter.write(response);
    }

    /**
     * Handle a parsed JSON-RPC request.
     *
     * @param request the request object as the plain-map JSON model.
     * @return the response object, or {@code null} when the message is a notification (no response is due).
     */
    public Map<String, Object> handleMessage(Map<String, Object> request) {
        Object methodObj = request.get("method");
        boolean isNotification = !request.containsKey("id");
        Object id = request.get("id");

        if (!(methodObj instanceof String)) {
            if (isNotification) {
                return null;
            }
            return JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Request is missing a string 'method'");
        }
        String method = (String) methodObj;
        Map<String, Object> params = asObject(request.get("params"));

        try {
            if ("initialize".equals(method)) {
                return JsonRpc.result(id, buildInitializeResult(params));
            }
            if ("ping".equals(method)) {
                return JsonRpc.result(id, new LinkedHashMap<String, Object>());
            }
            if ("tools/list".equals(method)) {
                return JsonRpc.result(id, buildToolsListResult());
            }
            if ("tools/call".equals(method)) {
                return JsonRpc.result(id, handleToolsCall(params));
            }
            if (method.startsWith("notifications/")) {
                // Notifications never receive a response.
                return null;
            }
            if (isNotification) {
                return null;
            }
            return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Method not found: " + method);
        } catch (InvalidParamsException e) {
            return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, e.getMessage());
        }
    }

    private Map<String, Object> buildInitializeResult(Map<String, Object> params) {
        Object requested = params.get("protocolVersion");
        String negotiated = defaultProtocolVersion;
        if (requested instanceof String && SUPPORTED_PROTOCOL_VERSIONS.contains(requested)) {
            negotiated = (String) requested;
        }

        Map<String, Object> tools = new LinkedHashMap<String, Object>();
        tools.put("listChanged", Boolean.FALSE);
        Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("tools", tools);

        Map<String, Object> serverInfo = new LinkedHashMap<String, Object>();
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("protocolVersion", negotiated);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        if (instructions != null) {
            result.put("instructions", instructions);
        }
        return result;
    }

    private Map<String, Object> buildToolsListResult() {
        List<Object> toolList = new ArrayList<Object>();
        for (McpTool tool : registry.all()) {
            Map<String, Object> def = new LinkedHashMap<String, Object>();
            def.put("name", tool.name());
            if (tool.title() != null) {
                def.put("title", tool.title());
            }
            def.put("description", tool.description());
            def.put("inputSchema", tool.inputSchema());
            // Every tool in this mod is a pure read-only introspector; advertise that so clients can surface it.
            Map<String, Object> annotations = new LinkedHashMap<String, Object>();
            if (tool.title() != null) {
                annotations.put("title", tool.title());
            }
            annotations.put("readOnlyHint", Boolean.TRUE);
            def.put("annotations", annotations);
            toolList.add(def);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tools", toolList);
        return result;
    }

    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        Object nameObj = params.get("name");
        if (!(nameObj instanceof String)) {
            throw new InvalidParamsException("tools/call requires a string 'name'");
        }
        String name = (String) nameObj;
        McpTool tool = registry.get(name);
        if (tool == null) {
            throw new InvalidParamsException("Unknown tool: " + name);
        }
        Map<String, Object> arguments = asObject(params.get("arguments"));

        ToolResult toolResult;
        try {
            toolResult = tool.call(arguments);
        } catch (McpToolException e) {
            toolResult = ToolResult.error(e.getMessage());
        } catch (RuntimeException e) {
            // A tool implementation bug must not take down the protocol connection; surface it as a tool error.
            toolResult = ToolResult.error(
                "Internal tool error: " + e.getClass()
                    .getSimpleName() + ": " + e.getMessage());
        }
        return buildCallEnvelope(toolResult);
    }

    private static Map<String, Object> buildCallEnvelope(ToolResult toolResult) {
        Map<String, Object> textBlock = new LinkedHashMap<String, Object>();
        textBlock.put("type", "text");
        textBlock.put("text", toolResult.text());
        List<Object> content = new ArrayList<Object>();
        content.add(textBlock);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("content", content);
        result.put("isError", Boolean.valueOf(toolResult.isError()));
        if (toolResult.structured() != null) {
            result.put("structuredContent", toolResult.structured());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    /** Internal signal that a request was structurally valid JSON-RPC but semantically malformed for its method. */
    private static final class InvalidParamsException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        InvalidParamsException(String message) {
            super(message);
        }
    }
}
