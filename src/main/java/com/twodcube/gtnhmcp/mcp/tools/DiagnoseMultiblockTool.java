package com.twodcube.gtnhmcp.mcp.tools;

import java.util.Map;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;
import com.twodcube.gtnhmcp.mcp.protocol.ToolArgs;
import com.twodcube.gtnhmcp.mcp.protocol.ToolResult;

/**
 * {@code diagnose_multiblock} — the headline tool. Inspects a GregTech multiblock controller (the looked-at block, or
 * an
 * explicit position) and reports structure-formed state, maintenance problems, active/idle status, progress,
 * efficiency,
 * the controller's energy buffer, and the in-game scanner lines, alongside a plain-language summary of likely causes.
 *
 * <p>
 * This is what answers "why is my multiblock not working / not forming / sitting idle". When the structure is not
 * formed
 * the model should follow up with {@link ScanBlocksTool} to compare the built blocks against the machine's required
 * structure.
 */
public final class DiagnoseMultiblockTool extends AbstractIntrospectionTool {

    public DiagnoseMultiblockTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        super(executor, introspector);
    }

    @Override
    public String name() {
        return "diagnose_multiblock";
    }

    @Override
    public String title() {
        return "Diagnose GregTech multiblock";
    }

    @Override
    public String description() {
        return "Diagnoses a GregTech multiblock controller and explains why it is or isn't working. Targets the "
            + "controller the player is looking at, or the controller at an explicit position. Reports: whether the "
            + "structure is formed, how many maintenance problems exist and which tools fix them, whether the machine is "
            + "active or idle, recipe progress, efficiency, the controller energy buffer, and the in-game scanner text, "
            + "plus a plain-language summary. Use this for 'why is my multiblock not forming / not running / idle'. If "
            + "the structure is not formed, follow up with scan_blocks to compare the built blocks to the required shape.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = Schema.props();
        properties.put(
            "pos",
            Schema.intTriple("Controller coordinates [x, y, z]. Omit to diagnose the block you are looking at."));
        return Schema.object(properties);
    }

    @Override
    public ToolResult call(Map<String, Object> arguments) throws McpToolException {
        ToolArgs args = new ToolArgs(arguments);
        final int[] pos = args.has("pos") ? args.requireIntTriple("pos") : null;
        return ToolResult.data(executor.call(() -> introspector.diagnoseMultiblock(pos)));
    }
}
