package com.twodcube.gtnhmcp.mcp.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.twodcube.gtnhmcp.mcp.protocol.McpDispatcher;

/**
 * Hosts the MCP server over the "Streamable HTTP" transport using the JDK's built-in {@link HttpServer}.
 *
 * <p>
 * Design choices, and why:
 * <ul>
 * <li><b>Why {@code com.sun.net.httpserver} instead of an MCP SDK?</b> The official MCP Java SDK requires Java 17+, but
 * this is a Minecraft 1.7.10 mod compiled to Java 8 bytecode. {@code com.sun.net.httpserver} ships in every JDK (Java
 * 6+,
 * and present in modern JDKs via the {@code jdk.httpserver} module), so it needs no shaded dependency.</li>
 * <li><b>Why loopback-only?</b> The server is bound to {@code 127.0.0.1} so it is only reachable from the local
 * machine.
 * This is the primary safety boundary — the tools expose read-only game state, but it should still never be exposed to
 * a
 * network. An optional bearer token adds defense in depth.</li>
 * <li><b>Why single-JSON responses (no SSE)?</b> All tools are request/response; the Streamable HTTP spec permits a
 * server to answer a POST with a single {@code application/json} body, so we never open an SSE stream. {@code GET}
 * (the SSE channel) is answered with {@code 405}, which compliant clients tolerate.</li>
 * </ul>
 *
 * <p>
 * Request handling runs on a small worker pool; each tool is responsible for marshalling its own work onto the
 * Minecraft
 * client thread, so the HTTP worker threads never touch game state directly.
 */
public final class McpHttpServer {

    private static final Logger LOG = LogManager.getLogger("gtnhmcp");
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    /** Reject request bodies larger than this to avoid a trivial memory-exhaustion vector. */
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final McpDispatcher dispatcher;
    private final String bindHost;
    private final int port;
    private final String authToken;
    private final int workerThreads;

    private HttpServer server;
    private ExecutorService executor;

    /**
     * @param dispatcher    the protocol dispatcher to feed request bodies into; must not be null.
     * @param bindHost      the loopback host to bind (typically {@code 127.0.0.1}).
     * @param port          the TCP port to listen on.
     * @param authToken     optional bearer token; when non-empty, requests must send
     *                      {@code Authorization: Bearer <token>}.
     * @param workerThreads size of the request-handling thread pool (>= 1).
     */
    public McpHttpServer(McpDispatcher dispatcher, String bindHost, int port, String authToken, int workerThreads) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher must not be null");
        }
        this.dispatcher = dispatcher;
        this.bindHost = bindHost == null || bindHost.trim()
            .isEmpty() ? "127.0.0.1" : bindHost.trim();
        this.port = port;
        this.authToken = authToken == null ? "" : authToken.trim();
        this.workerThreads = Math.max(1, workerThreads);
    }

    /**
     * Bind the socket and begin serving. Idempotent guard: calling start twice throws.
     *
     * @throws IOException if the socket cannot be bound (e.g. the port is already in use).
     */
    public synchronized void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("server already started");
        }
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(bindHost), port);
        server = HttpServer.create(address, 0);
        executor = Executors.newFixedThreadPool(workerThreads, new NamedDaemonThreadFactory());
        server.setExecutor(executor);
        server.createContext("/mcp", new McpHandler());
        server.start();
        LOG.info("GTNH MCP server listening on http://{}:{}/mcp", bindHost, port);
    }

    /**
     * Stop serving and release the socket and worker pool. Safe to call when not started.
     *
     * @param delaySeconds maximum number of seconds to wait for in-flight exchanges to finish.
     */
    public synchronized void stop(int delaySeconds) {
        if (server != null) {
            server.stop(Math.max(0, delaySeconds));
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        LOG.info("GTNH MCP server stopped");
    }

    /** @return the port the server was configured with. */
    public int port() {
        return port;
    }

    /**
     * @return the actual bound port (useful when configured with port {@code 0} to pick an ephemeral port), or the
     *         configured port when the server is not running.
     */
    public synchronized int boundPort() {
        return server == null ? port
            : server.getAddress()
                .getPort();
    }

    /** Handles POSTs to {@code /mcp}; rejects other methods the way the Streamable HTTP transport expects. */
    private final class McpHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders()
                        .set("Allow", "POST, OPTIONS");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                if (!"POST".equalsIgnoreCase(method)) {
                    // GET is the SSE channel of Streamable HTTP, which this server does not provide.
                    exchange.getResponseHeaders()
                        .set("Allow", "POST, OPTIONS");
                    sendJson(exchange, 405, "{\"error\":\"Use HTTP POST for MCP messages\"}");
                    return;
                }
                if (!isAuthorized(exchange)) {
                    exchange.getResponseHeaders()
                        .set("WWW-Authenticate", "Bearer");
                    sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                    return;
                }

                String body = readBody(exchange);
                String responseBody = dispatcher.handleRaw(body);
                if (responseBody.isEmpty()) {
                    // Notification: acknowledged, no content to return.
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }
                sendJson(exchange, 200, responseBody);
            } catch (BodyTooLargeException e) {
                sendJson(exchange, 413, "{\"error\":\"Request body too large\"}");
            } catch (RuntimeException e) {
                LOG.warn("Unexpected error handling MCP request", e);
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            } finally {
                exchange.close();
            }
        }

        private boolean isAuthorized(HttpExchange exchange) {
            if (authToken.isEmpty()) {
                return true;
            }
            String header = exchange.getRequestHeaders()
                .getFirst("Authorization");
            return header != null && header.equals("Bearer " + authToken);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > MAX_BODY_BYTES) {
                throw new BodyTooLargeException();
            }
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(UTF_8);
        exchange.getResponseHeaders()
            .set("Content-Type", JSON_CONTENT_TYPE);
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.flush();
    }

    /** Daemon threads so an undrained pool can never keep the Minecraft process alive on shutdown. */
    private static final class NamedDaemonThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "gtnhmcp-http-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class BodyTooLargeException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}
