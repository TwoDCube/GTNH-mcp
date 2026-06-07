package com.twodcube.gtnhmcp.mcp.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.twodcube.gtnhmcp.mcp.json.JsonParser;
import com.twodcube.gtnhmcp.mcp.protocol.McpDispatcher;
import com.twodcube.gtnhmcp.mcp.protocol.McpTool;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolRegistry;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * Integration tests for {@link McpHttpServer} over a real loopback socket (ephemeral port), exercising the full
 * transport: HTTP method handling, status codes, auth, and JSON-RPC round-trips. No Minecraft is involved.
 */
class McpHttpServerTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private McpHttpServer server;
    private String endpoint;

    /** Minimal tool so tools/call has something to invoke. */
    private static final class PingTool implements McpTool {

        @Override
        public String name() {
            return "ping_tool";
        }

        @Override
        public String title() {
            return "Ping";
        }

        @Override
        public String description() {
            return "Returns pong.";
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
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("pong", Boolean.TRUE);
            return ToolResult.data(data);
        }
    }

    private static McpDispatcher dispatcher() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new PingTool());
        return new McpDispatcher("GTNH MCP Test", "1.0", "2025-06-18", null, registry);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new McpHttpServer(dispatcher(), "127.0.0.1", 0, "", 2);
        server.start();
        endpoint = "http://127.0.0.1:" + server.boundPort() + "/mcp";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializeRoundTrip() throws IOException {
        Response r = post(
            endpoint,
            null,
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}");
        assertEquals(200, r.status);
        Map<String, Object> response = (Map<String, Object>) JsonParser.parse(r.body);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertEquals("2025-06-18", result.get("protocolVersion"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolsCallRoundTrip() throws IOException {
        Response r = post(
            endpoint,
            null,
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"ping_tool\"}}");
        assertEquals(200, r.status);
        Map<String, Object> response = (Map<String, Object>) JsonParser.parse(r.body);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        Map<String, Object> structured = (Map<String, Object>) result.get("structuredContent");
        assertEquals(Boolean.TRUE, structured.get("pong"));
    }

    @Test
    void notificationReturns202WithNoBody() throws IOException {
        Response r = post(endpoint, null, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertEquals(202, r.status);
        assertTrue(r.body.isEmpty());
    }

    @Test
    void getIsRejectedWith405() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("GET");
        int status = c.getResponseCode();
        c.disconnect();
        assertEquals(405, status);
    }

    @Test
    void authTokenIsEnforced() throws IOException {
        McpHttpServer secured = new McpHttpServer(dispatcher(), "127.0.0.1", 0, "s3cret", 1);
        secured.start();
        try {
            String url = "http://127.0.0.1:" + secured.boundPort() + "/mcp";
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
            assertEquals(401, post(url, null, body).status);
            assertEquals(401, post(url, "Bearer wrong", body).status);
            assertEquals(200, post(url, "Bearer s3cret", body).status);
        } finally {
            secured.stop(0);
        }
    }

    private static Response post(String url, String authHeader, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (authHeader != null) {
            c.setRequestProperty("Authorization", authHeader);
        }
        OutputStream out = c.getOutputStream();
        out.write(body.getBytes(UTF_8));
        out.close();
        int status = c.getResponseCode();
        InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String responseBody = readAll(stream);
        c.disconnect();
        return new Response(status, responseBody);
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        in.close();
        return new String(buffer.toByteArray(), UTF_8);
    }

    private static final class Response {

        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
