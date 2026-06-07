package com.twodcube.gtnhmcp.mcp.tools;

import java.util.Map;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolArgs;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * {@code scan_blocks} — surveys a cube of blocks around the player (or an explicit center) and returns a sorted count
 * of
 * every block type plus a list of tile entities (GregTech machines/hatches annotated).
 *
 * <p>
 * Because it reads the real client-side blocks, this is the most reliable way to answer "what did I actually build" —
 * the
 * model can compare the returned block list against a machine's required structure to find the wrong/missing block that
 * is preventing a multiblock from forming.
 */
public final class ScanBlocksTool extends AbstractIntrospectionTool {

    private static final int DEFAULT_RADIUS = 5;
    private static final int MAX_RADIUS = 16;
    private static final int DEFAULT_MAX_TILES = 64;
    private static final int MAX_MAX_TILES = 512;

    public ScanBlocksTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        super(executor, introspector);
    }

    @Override
    public String name() {
        return "scan_blocks";
    }

    @Override
    public String title() {
        return "Scan nearby blocks";
    }

    @Override
    public String description() {
        return "Scans a cube of blocks around the player (or an explicit center) and returns a sorted count of every "
            + "block type found (display name + registry id + metadata) plus a list of tile entities, with GregTech "
            + "machines and hatches annotated. Use this to see exactly what is built around a multiblock controller and "
            + "compare it against the required structure. Centering on the controller position is recommended.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = Schema.props();
        properties.put(
            "radius",
            Schema.integer(
                "Half-extent of the scan cube in blocks (1-" + MAX_RADIUS + "). Default " + DEFAULT_RADIUS + ".",
                1,
                MAX_RADIUS));
        properties.put("center", Schema.intTriple("Cube center [x, y, z]. Omit to center on the player's position."));
        properties.put("include_air", Schema.bool("Count air blocks too. Default false."));
        properties.put(
            "max_tiles",
            Schema.integer(
                "Maximum number of tile entities to list (1-" + MAX_MAX_TILES + "). Default " + DEFAULT_MAX_TILES + ".",
                1,
                MAX_MAX_TILES));
        return Schema.object(properties);
    }

    @Override
    public ToolResult call(Map<String, Object> arguments) throws McpToolException {
        ToolArgs args = new ToolArgs(arguments);
        final int radius = args.optInt("radius", DEFAULT_RADIUS, 1, MAX_RADIUS);
        final int[] center = args.has("center") ? args.requireIntTriple("center") : null;
        final boolean includeAir = args.optBoolean("include_air", false);
        final int maxTiles = args.optInt("max_tiles", DEFAULT_MAX_TILES, 1, MAX_MAX_TILES);
        return ToolResult.data(executor.call(() -> introspector.scan(radius, center, includeAir, maxTiles)));
    }
}
