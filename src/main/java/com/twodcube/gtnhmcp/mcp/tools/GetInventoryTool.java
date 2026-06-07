package com.twodcube.gtnhmcp.mcp.tools;

import java.util.Map;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * {@code get_inventory} — lists the player's held item, hotbar/main inventory, and worn armor, with each stack's
 * display
 * name, registry id, count, and metadata. Empty slots are omitted.
 */
public final class GetInventoryTool extends AbstractIntrospectionTool {

    public GetInventoryTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        super(executor, introspector);
    }

    @Override
    public String name() {
        return "get_inventory";
    }

    @Override
    public String title() {
        return "Get player inventory";
    }

    @Override
    public String description() {
        return "Returns the player's inventory: the held item, the selected hotbar slot, all non-empty main-inventory "
            + "slots, and worn armor. Each item includes its display name, registry id (mod:name), stack count, and "
            + "metadata. Use this to check whether the player is holding the right tool/material.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schema.noArgs();
    }

    @Override
    public ToolResult call(Map<String, Object> arguments) throws McpToolException {
        return ToolResult.data(executor.call(introspector::describeInventory));
    }
}
