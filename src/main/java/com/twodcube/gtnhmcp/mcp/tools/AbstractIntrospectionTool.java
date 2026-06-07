package com.twodcube.gtnhmcp.mcp.tools;

import com.twodcube.gtnhmcp.client.ClientThreadExecutor;
import com.twodcube.gtnhmcp.client.GameIntrospector;
import com.twodcube.gtnhmcp.mcp.protocol.McpTool;

/**
 * Shared wiring for every tool in this mod: each holds the {@link ClientThreadExecutor} used to hop onto the Minecraft
 * client thread and the {@link GameIntrospector} that performs the actual read-only state reads. Subclasses contribute
 * only the tool metadata (name/title/description/schema) and the body of {@code call}.
 */
abstract class AbstractIntrospectionTool implements McpTool {

    protected final ClientThreadExecutor executor;
    protected final GameIntrospector introspector;

    AbstractIntrospectionTool(ClientThreadExecutor executor, GameIntrospector introspector) {
        if (executor == null || introspector == null) {
            throw new IllegalArgumentException("executor and introspector must not be null");
        }
        this.executor = executor;
        this.introspector = introspector;
    }
}
