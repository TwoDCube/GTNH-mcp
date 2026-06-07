package com.twodcube.gtnhmcp.mcp.tools;

import java.util.Map;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * {@code get_target} — reports the block the player's crosshair is on, including any tile entity and GregTech identity.
 * This is the natural first call: it tells the model what the player is actually pointing at before deeper inspection.
 */
public final class GetTargetTool extends AbstractIntrospectionTool {

    public GetTargetTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        super(executor, introspector);
    }

    @Override
    public String name() {
        return "get_target";
    }

    @Override
    public String title() {
        return "Inspect the targeted block";
    }

    @Override
    public String description() {
        return "Returns details about the block the player is currently looking at (the crosshair target): display "
            + "name, registry id (mod:name), metadata, and whether it has a tile entity. If it is a GregTech machine, "
            + "also returns the machine name, meta-tile id, and whether it is a multiblock controller. Call this first "
            + "to identify what the player is pointing at.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schema.noArgs();
    }

    @Override
    public ToolResult call(Map<String, Object> arguments) throws McpToolException {
        return ToolResult.data(executor.call(introspector::describeTarget));
    }
}
