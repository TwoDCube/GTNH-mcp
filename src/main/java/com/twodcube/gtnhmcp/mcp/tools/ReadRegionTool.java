package com.twodcube.gtnhmcp.mcp.tools;

import java.util.Map;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolArgs;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * {@code read_region} — returns every block in an inclusive coordinate box with its <em>exact</em> coordinates.
 *
 * <p>
 * Unlike {@link ScanBlocksTool} (which only aggregates type counts over a cube and so can't say <em>where</em> a block
 * is), this tool returns one entry per cell, which is what lets a multiblock be verified cell-by-cell against a known
 * structure pattern. The volume is bounded ({@link #MAX_CELLS}) so a request can't return an unbounded payload.
 */
public final class ReadRegionTool extends AbstractIntrospectionTool {

    /** Maximum number of cells (the box volume) a single request may cover. */
    static final int MAX_CELLS = 4096;

    public ReadRegionTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        super(executor, introspector);
    }

    @Override
    public String name() {
        return "read_region";
    }

    @Override
    public String title() {
        return "Read exact blocks in a region";
    }

    @Override
    public String description() {
        return "Returns every block in an inclusive box between two corners, each with its EXACT coordinates, registry "
            + "id, metadata, display name, and any GregTech annotation. Use this (not scan_blocks, which only counts "
            + "types) to verify a multiblock cell-by-cell against its required structure. The box volume is capped at "
            + MAX_CELLS
            + " cells; set include_air to also return empty cells (useful for spotting gaps in a structure).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = Schema.props();
        properties.put("min", Schema.intTriple("Inclusive minimum corner [x, y, z]."));
        properties.put("max", Schema.intTriple("Inclusive maximum corner [x, y, z]; each component must be >= min."));
        properties.put("include_air", Schema.bool("Also return air cells (to spot gaps). Default false."));
        return Schema.object(properties, "min", "max");
    }

    @Override
    public ToolResult call(Map<String, Object> arguments) throws McpToolException {
        ToolArgs args = new ToolArgs(arguments);
        final int[] min = args.requireIntTriple("min");
        final int[] max = args.requireIntTriple("max");
        validateBounds(min, max);
        final boolean includeAir = args.optBoolean("include_air", false);
        return ToolResult.data(executor.call(() -> introspector.readRegion(min, max, includeAir)));
    }

    /**
     * Validate the box corners and that the enclosed volume is within {@link #MAX_CELLS}.
     *
     * @param min inclusive minimum corner.
     * @param max inclusive maximum corner.
     * @return the box volume in cells.
     * @throws McpToolException if any {@code max} component is below {@code min}, or the volume exceeds
     *                          {@link #MAX_CELLS}.
     */
    static long validateBounds(int[] min, int[] max) throws McpToolException {
        long volume = 1L;
        String[] axes = { "x", "y", "z" };
        for (int i = 0; i < 3; i++) {
            if (max[i] < min[i]) {
                throw new McpToolException(
                    "max." + axes[i] + " (" + max[i] + ") must be >= min." + axes[i] + " (" + min[i] + ")");
            }
            volume *= (long) (max[i] - min[i]) + 1L;
        }
        if (volume > MAX_CELLS) {
            throw new McpToolException(
                "Requested region is too large: " + volume
                    + " cells (max "
                    + MAX_CELLS
                    + "). Read it in smaller boxes.");
        }
        return volume;
    }
}
