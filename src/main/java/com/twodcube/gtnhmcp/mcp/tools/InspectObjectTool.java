package com.twodcube.gtnhmcp.mcp.tools;

import java.util.Map;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolArgs;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * {@code inspect_object} — a generic, read-only reflection dump of the tile entity at the target (or looked-at) block.
 *
 * <p>
 * Returns every field (including private/inherited) and the result of every no-argument getter-style method
 * ({@code get/is/has/can/are/size/count}); for a GregTech machine it also dumps the meta tile entity. This is the
 * escape
 * hatch for reading state the purpose-built tools don't expose (e.g. {@code isAllowedToWork}, {@code motorTier}, module
 * lists) without adding a new field each time. It is read-only by construction: it reads fields and only invokes
 * getter-named no-arg methods, so it never calls mutators like {@code connect()} or {@code stopMachine()}.
 */
public final class InspectObjectTool extends AbstractIntrospectionTool {

    public InspectObjectTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        super(executor, introspector);
    }

    @Override
    public String name() {
        return "inspect_object";
    }

    @Override
    public String title() {
        return "Inspect tile entity (reflection)";
    }

    @Override
    public String description() {
        return "Read-only reflection dump of the tile entity at the target (or looked-at) block: all fields (including "
            + "private) and the result of every no-argument getter-style method (get/is/has/can/are/size/count). For a "
            + "GregTech machine it also dumps the meta tile entity (e.g. motorTier, isAllowedToWork, getNeededMotorTier, "
            + "module lists). Use this to read state the other tools don't expose. It never calls mutating methods.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = Schema.props();
        properties
            .put("pos", Schema.intTriple("Block coordinates [x, y, z]. Omit to inspect the block you're looking at."));
        return Schema.object(properties);
    }

    @Override
    public ToolResult call(Map<String, Object> arguments) throws McpToolException {
        ToolArgs args = new ToolArgs(arguments);
        final int[] pos = args.has("pos") ? args.requireIntTriple("pos") : null;
        return ToolResult.data(executor.call(() -> introspector.inspectObject(pos)));
    }
}
