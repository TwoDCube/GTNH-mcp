package com.twodcube.gtnhmcp.proxy;

import java.io.IOException;

import com.twodcube.gtnhmcp.GtnhMcp;
import com.twodcube.gtnhmcp.GtnhMcpConfig;
import com.twodcube.gtnhmcp.Tags;
import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.http.McpHttpServer;
import com.twodcube.gtnhmcp.mcp.protocol.McpDispatcher;
import com.twodcube.gtnhmcp.mcp.protocol.ToolRegistry;
import com.twodcube.gtnhmcp.mcp.tools.DiagnoseMultiblockTool;
import com.twodcube.gtnhmcp.mcp.tools.GetInventoryTool;
import com.twodcube.gtnhmcp.mcp.tools.GetPlayerTool;
import com.twodcube.gtnhmcp.mcp.tools.GetTargetTool;
import com.twodcube.gtnhmcp.mcp.tools.ReadRegionTool;
import com.twodcube.gtnhmcp.mcp.tools.ScanBlocksTool;

import cpw.mods.fml.common.event.FMLInitializationEvent;

/**
 * The client proxy: builds the tool set and starts the embedded MCP HTTP server when the game launches on a physical
 * client.
 *
 * <p>
 * The server is started during FML init (rather than when a world loads) so the MCP client can connect at any time; the
 * tools themselves return a clear "no world loaded" error until the player is actually in a world. All tools share one
 * {@link ClientThreadExecutor} (so game-state reads are marshalled onto the client thread) and one
 * {@link GameIntrospector}.
 */
public class ClientProxy extends CommonProxy {

    /** Guidance returned to the model from MCP {@code initialize}, so it knows what this server is for. */
    private static final String INSTRUCTIONS = "Read-only inspection of a running GregTech New Horizons Minecraft client. Use 'get_target' to identify the "
        + "block the player is looking at (includes a GregTech controller's front facing); 'diagnose_multiblock' to "
        + "find out why a GregTech multiblock is not forming or not running; 'scan_blocks' to tally block types around "
        + "a controller; 'read_region' to read the EXACT block at every coordinate in a box (use this, not scan_blocks, "
        + "to verify a structure cell-by-cell against its required pattern); 'get_player' and 'get_inventory' for "
        + "context. Everything is read-only and only returns data while the player is in a world.";

    private McpHttpServer server;

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (!GtnhMcpConfig.enabled) {
            GtnhMcp.LOG.info("GTNH MCP server is disabled in the config; not starting.");
            return;
        }

        GameIntrospector introspector = new GameIntrospector();
        ClientThreadExecutor executor = new ClientThreadExecutor(GtnhMcpConfig.clientThreadTimeoutMs);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new GetTargetTool(executor, introspector));
        registry.register(new DiagnoseMultiblockTool(executor, introspector));
        registry.register(new ScanBlocksTool(executor, introspector));
        registry.register(new ReadRegionTool(executor, introspector));
        registry.register(new GetPlayerTool(executor, introspector));
        registry.register(new GetInventoryTool(executor, introspector));

        McpDispatcher dispatcher = new McpDispatcher("GTNH MCP", Tags.VERSION, "2025-06-18", INSTRUCTIONS, registry);
        server = new McpHttpServer(
            dispatcher,
            GtnhMcpConfig.bindHost,
            GtnhMcpConfig.port,
            GtnhMcpConfig.authToken,
            GtnhMcpConfig.httpWorkerThreads);
        try {
            server.start();
            Runtime.getRuntime()
                .addShutdownHook(new Thread(this::stopServer, "gtnhmcp-shutdown"));
            GtnhMcp.LOG.info(
                "GregTech multiblock diagnosis is {}.",
                introspector.isGregTechLoaded() ? "available" : "unavailable (GregTech not detected)");
        } catch (IOException e) {
            GtnhMcp.LOG.error(
                "Failed to start the GTNH MCP server on {}:{} — is the port already in use?",
                GtnhMcpConfig.bindHost,
                Integer.valueOf(GtnhMcpConfig.port),
                e);
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
}
