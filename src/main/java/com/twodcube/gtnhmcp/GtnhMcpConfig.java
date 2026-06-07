package com.twodcube.gtnhmcp;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Loads and holds the mod's configuration from the standard Forge config file.
 *
 * <p>
 * The MCP server is exposed over a local TCP socket, so its settings are security-relevant: the bind host defaults to
 * loopback and an optional bearer token is available. Values are read once during pre-init and cached in static fields,
 * which the client proxy reads when starting the server. Each option carries an in-file comment so a server operator
 * can
 * understand it without consulting the source.
 */
public final class GtnhMcpConfig {

    private static final String CATEGORY_SERVER = "server";

    /** Whether the embedded MCP server should start at all. */
    public static boolean enabled = true;
    /** Loopback host to bind. Keep this {@code 127.0.0.1} unless you fully understand the exposure. */
    public static String bindHost = "127.0.0.1";
    /** TCP port for the MCP endpoint ({@code http://<bindHost>:<port>/mcp}). */
    public static int port = 25590;
    /** Optional bearer token; when non-empty, clients must send {@code Authorization: Bearer <token>}. */
    public static String authToken = "";
    /** Number of HTTP worker threads handling incoming MCP requests. */
    public static int httpWorkerThreads = 2;
    /** How long (ms) a request may wait for the Minecraft client thread before timing out with a clear error. */
    public static int clientThreadTimeoutMs = 5000;

    private GtnhMcpConfig() {}

    /**
     * Read the configuration file into the static fields above, validating and clamping where appropriate, and persist
     * any defaults that were missing.
     *
     * @param configFile the suggested configuration file from {@code FMLPreInitializationEvent}; must not be null.
     */
    public static void synchronize(File configFile) {
        if (configFile == null) {
            throw new IllegalArgumentException("configFile must not be null");
        }
        Configuration configuration = new Configuration(configFile);
        try {
            enabled = configuration.getBoolean(
                "enabled",
                CATEGORY_SERVER,
                enabled,
                "Master switch for the embedded MCP server. The mod is harmless when disabled.");
            bindHost = configuration.getString(
                "bindHost",
                CATEGORY_SERVER,
                bindHost,
                "Host/IP to bind the MCP server to. Keep this 127.0.0.1 (loopback) so it is only reachable from this "
                    + "machine. Changing it exposes read-only game state to your network.");
            port = configuration.getInt(
                "port",
                CATEGORY_SERVER,
                port,
                1024,
                65535,
                "TCP port for the MCP endpoint, served at http://<bindHost>:<port>/mcp .");
            authToken = configuration.getString(
                "authToken",
                CATEGORY_SERVER,
                authToken,
                "Optional bearer token. When non-empty, MCP clients must send 'Authorization: Bearer <token>'. Leave "
                    + "blank for no auth (safe when bound to loopback).");
            httpWorkerThreads = configuration.getInt(
                "httpWorkerThreads",
                CATEGORY_SERVER,
                httpWorkerThreads,
                1,
                16,
                "Number of HTTP worker threads handling MCP requests.");
            clientThreadTimeoutMs = configuration.getInt(
                "clientThreadTimeoutMs",
                CATEGORY_SERVER,
                clientThreadTimeoutMs,
                250,
                60000,
                "Milliseconds a request will wait for the Minecraft client thread before returning a timeout error.");
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }
}
