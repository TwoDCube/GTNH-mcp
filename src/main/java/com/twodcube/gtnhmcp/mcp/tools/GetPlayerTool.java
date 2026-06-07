package com.twodcube.gtnhmcp.mcp.tools;

import java.util.Map;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * {@code get_player} — reports the player's position, dimension, health/food, held item, and a brief note of what they
 * are looking at. Useful for grounding answers in where the player is and what they are holding.
 */
public final class GetPlayerTool extends AbstractIntrospectionTool {

    public GetPlayerTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        super(executor, introspector);
    }

    @Override
    public String name() {
        return "get_player";
    }

    @Override
    public String title() {
        return "Get player state";
    }

    @Override
    public String description() {
        return "Returns the player's current position, block position, dimension (id and name), health, food level, "
            + "the item currently held, and a brief note of the block they are looking at.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schema.noArgs();
    }

    @Override
    public ToolResult call(Map<String, Object> arguments) throws McpToolException {
        return ToolResult.data(executor.call(introspector::describePlayer));
    }
}
