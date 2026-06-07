package com.twodcube.gtnhmcp.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.twodcube.gtnhmcp.mcp.json.JsonParser;

/** Unit tests for {@link McpDispatcher}: the MCP request/response subset, error mapping, and notifications. */
class McpDispatcherTest {

    private McpDispatcher dispatcher;

    /** A trivial tool used to exercise tools/list and tools/call without any Minecraft dependency. */
    private static final class EchoTool implements McpTool {

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String title() {
            return "Echo";
        }

        @Override
        public String description() {
            return "Echoes the 'msg' argument back.";
        }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> schema = new LinkedHashMap<String, Object>();
            schema.put("type", "object");
            schema.put("properties", new LinkedHashMap<String, Object>());
            return schema;
        }

        @Override
        public ToolResult call(Map<String, Object> arguments) throws McpToolException {
            if (arguments.containsKey("fail")) {
                throw new McpToolException("boom");
            }
            if (arguments.containsKey("crash")) {
                throw new IllegalStateException("unexpected");
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("echoed", arguments.get("msg"));
            return ToolResult.data(data);
        }
    }

    @BeforeEach
    void setUp() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        dispatcher = new McpDispatcher("Test Server", "9.9", "2025-06-18", "use me", registry);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String request) {
        String raw = dispatcher.handleRaw(request);
        if (raw.isEmpty()) {
            return null;
        }
        return (Map<String, Object>) JsonParser.parse(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultOf(Map<String, Object> response) {
        assertNull(response.get("error"), "expected a result, got an error: " + response.get("error"));
        return (Map<String, Object>) response.get("result");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorOf(Map<String, Object> response) {
        return (Map<String, Object>) response.get("error");
    }

    @Test
    void initializeEchoesSupportedProtocolVersion() {
        Map<String, Object> response = call(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}");
        assertEquals("2.0", response.get("jsonrpc"));
        assertEquals(Long.valueOf(1), response.get("id"));
        Map<String, Object> result = resultOf(response);
        assertEquals("2025-06-18", result.get("protocolVersion"));
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("Test Server", serverInfo.get("name"));
        assertEquals("9.9", serverInfo.get("version"));
        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        assertTrue(capabilities.containsKey("tools"));
        assertEquals("use me", result.get("instructions"));
    }

    @Test
    void initializeNegotiatesDownUnknownVersion() {
        Map<String, Object> result = resultOf(
            call(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}"));
        assertEquals("2025-06-18", result.get("protocolVersion"));
    }

    @Test
    void toolsListReturnsRegisteredTool() {
        Map<String, Object> result = resultOf(call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));
        @SuppressWarnings("unchecked")
        List<Object> tools = (List<Object>) result.get("tools");
        assertEquals(1, tools.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> tool = (Map<String, Object>) tools.get(0);
        assertEquals("echo", tool.get("name"));
        assertEquals("Echo", tool.get("title"));
        assertNotNull(tool.get("inputSchema"));
        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) tool.get("annotations");
        assertEquals(Boolean.TRUE, annotations.get("readOnlyHint"));
    }

    @Test
    void toolsCallSuccessReturnsContentAndStructured() {
        Map<String, Object> result = resultOf(
            call(
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"echo\",\"arguments\":{\"msg\":\"hello\"}}}"));
        assertEquals(Boolean.FALSE, result.get("isError"));
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) result.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> block = (Map<String, Object>) content.get(0);
        assertEquals("text", block.get("type"));
        assertTrue(((String) block.get("text")).contains("hello"));
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.get("structuredContent");
        assertEquals("hello", structured.get("echoed"));
    }

    @Test
    void toolExceptionBecomesIsErrorResultNotProtocolError() {
        Map<String, Object> result = resultOf(
            call(
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"echo\",\"arguments\":{\"fail\":true}}}"));
        assertEquals(Boolean.TRUE, result.get("isError"));
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) result.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> block = (Map<String, Object>) content.get(0);
        assertEquals("boom", block.get("text"));
    }

    @Test
    void unexpectedToolRuntimeExceptionBecomesIsErrorResult() {
        Map<String, Object> result = resultOf(
            call(
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"echo\",\"arguments\":{\"crash\":true}}}"));
        assertEquals(Boolean.TRUE, result.get("isError"));
    }

    @Test
    void unknownToolIsInvalidParams() {
        Map<String, Object> error = errorOf(
            call("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}"));
        assertEquals(Long.valueOf(JsonRpc.INVALID_PARAMS), error.get("code"));
    }

    @Test
    void unknownMethodIsMethodNotFound() {
        Map<String, Object> error = errorOf(call("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"does/not/exist\"}"));
        assertEquals(Long.valueOf(JsonRpc.METHOD_NOT_FOUND), error.get("code"));
    }

    @Test
    void pingReturnsEmptyResult() {
        Map<String, Object> result = resultOf(call("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\"}"));
        assertTrue(result.isEmpty());
    }

    @Test
    void notificationsProduceNoResponse() {
        assertNull(call("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        assertNull(call("{\"jsonrpc\":\"2.0\",\"method\":\"some/other/notification\"}"));
    }

    @Test
    void parseErrorReturnsMinus32700WithNullId() {
        Map<String, Object> error = errorOf(call("{not valid json"));
        assertEquals(Long.valueOf(JsonRpc.PARSE_ERROR), error.get("code"));
    }

    @Test
    void batchRequestRejected() {
        Map<String, Object> error = errorOf(call("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]"));
        assertEquals(Long.valueOf(JsonRpc.INVALID_REQUEST), error.get("code"));
    }

    @Test
    void requestMissingMethodIsInvalidRequest() {
        Map<String, Object> error = errorOf(call("{\"jsonrpc\":\"2.0\",\"id\":9}"));
        assertEquals(Long.valueOf(JsonRpc.INVALID_REQUEST), error.get("code"));
    }

    @Test
    void notificationMissingMethodProducesNoResponse() {
        assertNull(call("{\"jsonrpc\":\"2.0\"}"));
    }
}
